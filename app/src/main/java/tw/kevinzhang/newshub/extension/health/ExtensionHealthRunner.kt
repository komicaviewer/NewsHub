package tw.kevinzhang.newshub.extension.health

import android.os.DeadObjectException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.BoardQuery
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Executes trusted profiles sequentially so request count and failure attribution stay bounded. */
class ExtensionHealthRunner(
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(
        profile: ExtensionHealthProfile,
        sources: Collection<Source>,
        authenticatedSessionSourceIds: Set<String> = emptySet(),
        captureEvidence: suspend (sourceId: String) -> String? = { null },
    ): ExtensionHealthReport {
        val checkedProfile = profile.validated()
        val startedAt = now()
        val sourceById = sources.associateBy(Source::id)
        var requestCount = 0
        val results = mutableListOf<SourceHealthResult>()
        val deadline = startedAt + checkedProfile.runTimeoutMs

        for (sourceProfile in checkedProfile.sources) {
            if (now() >= deadline) {
                results += SourceHealthResult(
                    sourceId = sourceProfile.sourceId,
                    packageName = sourceProfile.packageName,
                    status = HealthStatus.FAIL,
                    durationMs = 0,
                    steps = listOf(failedStep(sourceProfile, "run_timeout", HealthFailureClass.TIMEOUT, 0)),
                )
                continue
            }
            val sourceStartedAt = now()
            val steps = mutableListOf<HealthStepResult>()
            val source = sourceById[sourceProfile.sourceId]
            if (source == null) {
                steps += failedStep(sourceProfile, "load_source", HealthFailureClass.HOST_RUNTIME, 0)
            } else {
                val identityMatches = source.sourceIdentity?.let { identity ->
                    identity.packageName == sourceProfile.packageName &&
                        identity.sourceId == sourceProfile.sourceId
                } == true
                if (!identityMatches) {
                    steps += failedStep(sourceProfile, "verify_identity", HealthFailureClass.HOST_RUNTIME, 0)
                } else {
                    suspend fun <T> request(
                        operation: String,
                        block: suspend () -> T,
                        validate: (T) -> Int?,
                    ): T? {
                        if (requestCount >= checkedProfile.maxRequests) {
                            steps += failedStep(
                                sourceProfile,
                                operation,
                                HealthFailureClass.HOST_RUNTIME,
                                0,
                            )
                            return null
                        }
                        requestCount += 1
                        val operationStartedAt = now()
                        return try {
                            val remainingRunMs = (deadline - now()).coerceAtLeast(1)
                            val timeoutMs = minOf(checkedProfile.operationTimeoutMs, remainingRunMs)
                            val result = withTimeoutOrNull(timeoutMs) { block() }
                            if (result == null) {
                                steps += failedStep(
                                    sourceProfile,
                                    operation,
                                    HealthFailureClass.TIMEOUT,
                                    elapsed(operationStartedAt),
                                )
                                return null
                            }
                            val observedCount = validate(result)
                            steps += HealthStepResult(
                                operation = operation,
                                status = HealthStatus.PASS,
                                durationMs = elapsed(operationStartedAt),
                                observedCount = observedCount,
                            )
                            result
                        } catch (error: Exception) {
                            if (error is CancellationException && error !is TimeoutCancellationException) {
                                throw error
                            }
                            val classification = classifyFailure(error)
                            steps += failedStep(
                                sourceProfile,
                                operation,
                                classification,
                                elapsed(operationStartedAt),
                                error,
                            )
                            null
                        }
                    }

                    var continueProbe = true
                    if (sourceProfile.requireAuthenticatedSession) {
                        if (sourceProfile.sourceId !in authenticatedSessionSourceIds) {
                            steps += pendingStep(sourceProfile, "session_provision", 0)
                            continueProbe = false
                        } else {
                            val authenticated = request(
                                operation = "validate_session",
                                block = {
                                    require(source is AuthenticatedSource) {
                                        "Configured source does not implement authentication"
                                    }
                                    if (!source.validateSession()) throw AuthenticationRequiredException()
                                    true
                                },
                                validate = { null },
                            )
                            continueProbe = authenticated == true
                        }
                    }

                    val board = if (continueProbe) {
                        request(
                            operation = "get_board_page",
                            block = {
                                val page = source.getBoardPage(
                                    BoardPageRequest(
                                        query = BoardQuery(sourceProfile.boardQuery),
                                        pageSize = 20,
                                    ),
                                )
                                val selected = selectBoard(page.boards, sourceProfile)
                                requireHostAllowed(selected.url, sourceProfile.allowedHosts)
                                selected
                            },
                            validate = { 1 },
                        )
                    } else null

                    val summaries = if (board != null) {
                        request(
                            operation = "get_thread_summaries",
                            block = {
                                source.getThreadSummaries(board, 1).also { summaries ->
                                    require(summaries.size >= sourceProfile.minimumSummaries) {
                                        "Summary contract failed"
                                    }
                                    require(summaries.all { summary ->
                                        summary.sourceId == source.id && summary.id.isNotBlank() &&
                                            summary.boardUrl.isNotBlank() &&
                                            (summary.title?.isNotBlank() == true || summary.previewContent.isNotEmpty())
                                    }) { "Summary fields failed contract" }
                                    summaries.forEach { summary ->
                                        requireHostAllowed(summary.boardUrl, sourceProfile.allowedHosts)
                                    }
                                }
                            },
                            validate = List<*>::size,
                        )
                    } else null

                    if (!summaries.isNullOrEmpty()) {
                        request(
                            operation = "get_thread_page",
                            block = {
                                source.getThreadPage(summaries.first(), null).also { page ->
                                    require(page.posts.size >= sourceProfile.minimumPosts) {
                                        "Thread contract failed"
                                    }
                                    require(page.posts.all { it.id.isNotBlank() && it.content.isNotEmpty() }) {
                                        "Post fields failed contract"
                                    }
                                }
                            },
                            validate = { it.posts.size },
                        )
                    }
                }
            }

            val status = when {
                steps.isNotEmpty() && steps.all { it.status == HealthStatus.PASS } -> HealthStatus.PASS
                steps.isNotEmpty() && steps.all {
                    it.status == HealthStatus.PASS || it.status == HealthStatus.AUTH_PENDING
                } && steps.any { it.status == HealthStatus.AUTH_PENDING } -> HealthStatus.AUTH_PENDING
                else -> HealthStatus.FAIL
            }
            val evidenceScreenshot = if (status == HealthStatus.PASS) {
                try {
                    captureEvidence(sourceProfile.sourceId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            results += SourceHealthResult(
                sourceId = sourceProfile.sourceId,
                packageName = sourceProfile.packageName,
                status = status,
                durationMs = elapsed(sourceStartedAt),
                steps = steps,
                evidenceScreenshot = evidenceScreenshot,
            )
        }

        return ExtensionHealthReport(
            profileId = checkedProfile.profileId,
            startedAtEpochMs = startedAt,
            finishedAtEpochMs = now(),
            status = when {
                results.size != checkedProfile.sources.size || results.any { it.status == HealthStatus.FAIL } ->
                    HealthStatus.FAIL
                results.all { it.status == HealthStatus.PASS } -> HealthStatus.PASS
                results.any { it.status == HealthStatus.AUTH_PENDING } -> HealthStatus.PARTIAL_AUTH_PENDING
                else -> HealthStatus.FAIL
            },
            requestCount = requestCount,
            results = results,
        )
    }

    private fun elapsed(startedAt: Long): Long = (now() - startedAt).coerceAtLeast(0)
}

private fun selectBoard(boards: List<Board>, profile: SourceHealthProfile): Board {
    require(boards.isNotEmpty()) { "Board contract failed" }
    return when {
        profile.boardUrl != null -> boards.firstOrNull { it.url == profile.boardUrl }
        profile.boardNameContains != null -> boards.firstOrNull {
            it.name.contains(profile.boardNameContains, ignoreCase = true)
        }
        else -> boards.firstOrNull()
    } ?: throw IllegalStateException("Configured board was not returned")
}

private fun failedStep(
    profile: SourceHealthProfile,
    operation: String,
    failureClass: HealthFailureClass,
    durationMs: Long,
    error: Throwable? = null,
) = HealthStepResult(
    operation = operation,
    status = if (failureClass == HealthFailureClass.AUTH_REQUIRED) {
        HealthStatus.AUTH_PENDING
    } else {
        HealthStatus.FAIL
    },
    durationMs = durationMs,
    failureClass = failureClass,
    failureFingerprint = failureFingerprint(
        sourceId = profile.sourceId,
        operation = operation,
        failureClass = failureClass,
        packageName = profile.packageName,
    ),
    observedHost = (error as? HostPolicyViolationException)?.observedHost,
    allowedHosts = (error as? HostPolicyViolationException)?.allowedHosts?.sorted(),
)

private fun pendingStep(
    profile: SourceHealthProfile,
    operation: String,
    durationMs: Long,
) = failedStep(profile, operation, HealthFailureClass.AUTH_REQUIRED, durationMs)

/** Classifies with private exception text but never stores or logs that text. */
internal fun classifyFailure(error: Throwable): HealthFailureClass {
    if (error is TimeoutCancellationException || error is SocketTimeoutException) {
        return HealthFailureClass.TIMEOUT
    }
    if (error is AuthenticationRequiredException) return HealthFailureClass.AUTH_REQUIRED
    if (error is HostPolicyViolationException) return HealthFailureClass.HOST_POLICY
    if (error is DeadObjectException || error is SecurityException) return HealthFailureClass.HOST_RUNTIME

    val privateMessage = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.lowercase() }
        .joinToString(" ")
    return when {
        Regex("(?:http\\s*)?429\\b|rate.?limit|too many requests").containsMatchIn(privateMessage) ->
            HealthFailureClass.RATE_LIMITED
        Regex("(?:http\\s*)?(401|403)\\b|auth|login|sign[ -]?in|session").containsMatchIn(privateMessage) ->
            HealthFailureClass.AUTH_REQUIRED
        Regex("(?:http\\s*)?5\\d\\d\\b|unavailable|challenge|connection|network|dns").containsMatchIn(privateMessage) ->
            HealthFailureClass.SITE_UNAVAILABLE
        Regex("parse|parser|structure|missing|invalid document|contract").containsMatchIn(privateMessage) ->
            HealthFailureClass.PARSER_CONTRACT
        error is UnknownHostException || error is ConnectException || error is IOException ->
            HealthFailureClass.SITE_UNAVAILABLE
        error is IllegalArgumentException || error is IllegalStateException ->
            HealthFailureClass.PARSER_CONTRACT
        else -> HealthFailureClass.UNKNOWN
    }
}
