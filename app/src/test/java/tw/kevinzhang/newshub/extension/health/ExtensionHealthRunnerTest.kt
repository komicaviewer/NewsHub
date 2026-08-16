package tw.kevinzhang.newshub.extension.health

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.SourceRuntime
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_api.SourceFailureException
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class ExtensionHealthRunnerTest {
    @Test
    fun healthySourcePassesStructuralContractAndReportContainsNoContent() = runBlocking {
        val profile = loadProfile()

        val report = ExtensionHealthRunner().run(profile, listOf(FakeSource()))

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(3, report.requestCount)
        assertEquals(
            listOf("get_board_page", "get_thread_summaries", "get_thread_page"),
            report.results.single().steps.map(HealthStepResult::operation),
        )
        val json = ExtensionHealthJson.encodeReport(report)
        assertFalse(json.contains("private thread title"))
        assertFalse(json.contains("private post body"))
        assertFalse(json.contains("https://example.com/board"))
    }

    @Test
    fun malformedSummaryIsClassifiedWithoutLeakingExceptionText() = runBlocking {
        val source = FakeSource(summary = FakeSource.summary.copy(id = ""))
        var evidenceCalls = 0

        val report = ExtensionHealthRunner().run(
            loadProfile(),
            listOf(source),
            captureEvidence = {
                evidenceCalls += 1
                "screenshots/test-source.png"
            },
        )

        val failure = report.results.single().steps.last()
        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthFailureClass.PARSER_CONTRACT, failure.failureClass)
        assertTrue(failure.failureFingerprint?.matches(Regex("[a-f0-9]{24}")) == true)
        assertFalse(ExtensionHealthJson.encodeReport(report).contains("Summary fields failed contract"))
        assertEquals(0, evidenceCalls)
        assertNull(report.results.single().evidenceScreenshot)
    }

    @Test
    fun missingOrWrongRuntimeIdentityNeverCallsSource() = runBlocking {
        val source = FakeSource(sourceIdentity = SourceIdentity("other.extension", "a".repeat(64), "test.source"))

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))

        assertEquals(0, report.requestCount)
        assertEquals("verify_identity", report.results.single().steps.single().operation)
        assertEquals(HealthFailureClass.HOST_RUNTIME, report.results.single().steps.single().failureClass)
    }

    @Test
    fun missingSourceStillProducesSanitizedHostRuntimeReport() = runBlocking {
        val report = ExtensionHealthRunner().run(loadProfile(), emptyList())
        val json = ExtensionHealthJson.encodeReport(report)

        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(0, report.requestCount)
        assertEquals("load_source", report.results.single().steps.single().operation)
        assertEquals(HealthFailureClass.HOST_RUNTIME, report.results.single().steps.single().failureClass)
        assertFalse(json.contains("Exception"))
        assertFalse(json.contains("Timed out"))
    }

    @Test
    fun profileRejectsCredentialBearingOrUntrustedBoardUrl() {
        val fixture = resource("profile-v1.json")
        val credentialBearing = fixture.replace(
            "\"allowedHosts\": [\"example.com\"]",
            "\"allowedHosts\": [\"example.com\"], \"passwordSecret\": \"secret-name\"",
        )
        val userInfoUrl = fixture.replace(
            "\"allowedHosts\": [\"example.com\"]",
            "\"allowedHosts\": [\"example.com\"], \"boardUrl\": \"https://user:password@example.com/a\"",
        )

        assertThrows(RuntimeException::class.java) { ExtensionHealthJson.decodeProfile(credentialBearing) }
        assertThrows(RuntimeException::class.java) { ExtensionHealthJson.decodeProfile(userInfoUrl) }
    }

    @Test
    fun reportFailureHasOnlyStableSafeFields() = runBlocking {
        val secret = "session=super-secret"
        val source = FakeSource(boardFailure = java.io.IOException("HTTP 429 $secret"))

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))
        val json = ExtensionHealthJson.encodeReport(report)

        assertEquals(HealthFailureClass.RATE_LIMITED, report.results.single().steps.last().failureClass)
        assertFalse(json.contains(secret))
        assertNull(report.results.single().steps.last().observedCount)
    }

    @Test
    fun exactHostPolicyFailureIsDistinctAndCarriesOnlySafeHostEvidence() = runBlocking {
        val source = FakeSource(boardUrl = "https://gita.komica1.org/00b/pixmicat.php?secret=query")

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))
        val failure = report.results.single().steps.single()
        val json = ExtensionHealthJson.encodeReport(report)

        assertEquals(HealthFailureClass.HOST_POLICY, failure.failureClass)
        assertEquals("gita.komica1.org", failure.observedHost)
        assertEquals(listOf("example.com"), failure.allowedHosts)
        assertFalse(json.contains("pixmicat.php"))
        assertFalse(json.contains("secret=query"))
    }

    @Test
    fun typedBrokerHostPolicyFailureKeepsSafeOperationAndEvidence() = runBlocking {
        val source = FakeSource(
            boardFailure = SourceFailureException(
                SourceFailure(
                    code = SourceFailureCode.HOST_POLICY,
                    operation = "gamer_board_directory",
                    observedHost = "api.gamer.com.tw",
                    allowedHosts = listOf("forum.gamer.com.tw"),
                ),
            ),
        )

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))
        val failure = report.results.single().steps.single()
        val json = ExtensionHealthJson.encodeReport(report)

        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthFailureClass.HOST_POLICY, failure.failureClass)
        assertEquals("gamer_board_directory", failure.operation)
        assertEquals("api.gamer.com.tw", failure.observedHost)
        assertEquals(listOf("forum.gamer.com.tw"), failure.allowedHosts)
        assertFalse(json.contains("http"))
        assertFalse(json.contains("Exception"))
    }

    @Test
    fun authenticationFailureFromPublicBoardDirectoryFailsInsteadOfBeingAllowedPending() = runBlocking {
        val source = FakeSource(
            boardFailure = SourceFailureException(
                SourceFailure(SourceFailureCode.AUTH_REQUIRED, operation = "board_page"),
            ),
        )

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))

        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthStatus.FAIL, report.results.single().status)
        assertEquals(HealthStatus.FAIL, report.results.single().steps.single().status)
        assertEquals(HealthFailureClass.AUTH_REQUIRED, report.results.single().steps.single().failureClass)
    }

    @Test
    fun slowOperationStopsAtConfiguredTimeout() = runBlocking {
        val profile = loadProfile().copy(operationTimeoutMs = 1_000)

        val report = ExtensionHealthRunner().run(
            profile = profile,
            sources = listOf(FakeSource(boardDelayMs = 1_500)),
        )

        assertEquals(1, report.requestCount)
        assertEquals(HealthFailureClass.TIMEOUT, report.results.single().steps.single().failureClass)
    }

    @Test
    fun missingHostOwnedSessionIsPendingWithoutCallingCandidateCode() = runBlocking {
        val profile = loadProfile().copy(
            maxRequests = 4,
            sources = loadProfile().sources.map {
                it.copy(
                    requireAuthenticatedSession = true,
                    authenticatedOperations = setOf(
                        HealthProbeOperation.GET_THREAD_SUMMARIES,
                        HealthProbeOperation.GET_THREAD_PAGE,
                    ),
                )
            },
        )
        val source = FakeAuthenticatedSource()

        val report = ExtensionHealthRunner().run(profile, listOf(source))

        assertEquals(HealthStatus.PARTIAL_AUTH_PENDING, report.status)
        assertEquals(HealthStatus.AUTH_PENDING, report.results.single().status)
        assertEquals("session_provision", report.results.single().steps.single().operation)
        assertEquals(HealthFailureClass.AUTH_REQUIRED, report.results.single().steps.single().failureClass)
        assertEquals(0, report.requestCount)
        assertEquals(0, source.calls)
    }

    @Test
    fun provisionedButExpiredSessionRemainsPendingAndNeverLooksHealthy() = runBlocking {
        val profile = loadProfile().copy(
            maxRequests = 4,
            sources = loadProfile().sources.map {
                it.copy(
                    requireAuthenticatedSession = true,
                    authenticatedOperations = setOf(
                        HealthProbeOperation.GET_THREAD_SUMMARIES,
                        HealthProbeOperation.GET_THREAD_PAGE,
                    ),
                )
            },
        )
        val source = FakeAuthenticatedSource(sessionValid = false)
        var evidenceCalls = 0

        val report = ExtensionHealthRunner().run(
            profile,
            listOf(source),
            authenticatedSessionSourceIds = setOf("test.source"),
            captureEvidence = {
                evidenceCalls += 1
                "screenshots/test-source.png"
            },
        )

        assertEquals(HealthStatus.PARTIAL_AUTH_PENDING, report.status)
        assertEquals("validate_session", report.results.single().steps.single().operation)
        assertEquals(1, report.requestCount)
        assertEquals(1, source.calls)
        assertEquals(0, evidenceCalls)
        assertNull(report.results.single().evidenceScreenshot)
    }

    @Test
    fun publicProbeCallsBoardDirectoryBeforeMarkingOnlyProtectedOperationsPending() = runBlocking {
        val source = FakeAuthenticatedSource()
        val profile = loadProfile().copy(
            maxRequests = 1,
            sources = loadProfile().sources.map {
                it.copy(
                    authenticatedOperations = setOf(
                        HealthProbeOperation.GET_THREAD_SUMMARIES,
                        HealthProbeOperation.GET_THREAD_PAGE,
                    ),
                )
            },
        )

        val report = ExtensionHealthRunner().run(profile, listOf(source))

        assertEquals(HealthStatus.PARTIAL_AUTH_PENDING, report.status)
        assertEquals(1, report.requestCount)
        assertEquals(1, source.calls)
        assertEquals(
            listOf(
                HealthProbeOperation.GET_BOARD_PAGE.wireName to HealthStatus.PASS,
                HealthProbeOperation.GET_THREAD_SUMMARIES.wireName to HealthStatus.AUTH_PENDING,
                HealthProbeOperation.GET_THREAD_PAGE.wireName to HealthStatus.AUTH_PENDING,
            ),
            report.results.single().steps.map { it.operation to it.status },
        )
    }

    @Test
    fun fullCredentialedProbeValidatesSessionThenRunsEveryOperation() = runBlocking {
        val source = FakeAuthenticatedSource()
        val profile = loadProfile().copy(
            maxRequests = 4,
            sources = loadProfile().sources.map {
                it.copy(
                    requireAuthenticatedSession = true,
                    authenticatedOperations = setOf(
                        HealthProbeOperation.GET_THREAD_SUMMARIES,
                        HealthProbeOperation.GET_THREAD_PAGE,
                    ),
                )
            },
        )

        val report = ExtensionHealthRunner().run(
            profile,
            listOf(source),
            authenticatedSessionSourceIds = setOf("test.source"),
        )

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(4, report.requestCount)
        assertEquals(4, source.calls)
        assertEquals(
            listOf(
                "validate_session",
                HealthProbeOperation.GET_BOARD_PAGE.wireName,
                HealthProbeOperation.GET_THREAD_SUMMARIES.wireName,
                HealthProbeOperation.GET_THREAD_PAGE.wireName,
            ),
            report.results.single().steps.map(HealthStepResult::operation),
        )
    }

    @Test
    fun capturesEvidenceOnlyAfterSourcePasses() = runBlocking {
        var evidenceCalls = 0

        val report = ExtensionHealthRunner().run(
            profile = loadProfile(),
            sources = listOf(FakeSource()),
            captureEvidence = {
                evidenceCalls += 1
                "screenshots/test-source.png"
            },
        )

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(1, evidenceCalls)
        assertEquals("screenshots/test-source.png", report.results.single().evidenceScreenshot)
    }

    private fun loadProfile(): ExtensionHealthProfile = ExtensionHealthJson.decodeProfile(resource("profile-v1.json"))

    private fun resource(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("extension-health/$name"),
    ).readText()

    private class FakeSource(
        override val sourceIdentity: SourceIdentity? = SourceIdentity(
            packageName = "test.extension",
            signerSha256 = "a".repeat(64),
            sourceId = "test.source",
        ),
        private val summary: ThreadSummary = Companion.summary,
        private val boardFailure: Throwable? = null,
        private val boardDelayMs: Long = 0,
        private val boardUrl: String = "https://example.com/board",
    ) : Source {
        override val id = "test.source"
        override val name = "Test"
        override val language = "en"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false

        override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
            boardFailure?.let { throw it }
            if (boardDelayMs > 0) delay(boardDelayMs)
            return BoardPage(listOf(Board(id, boardUrl, "Board")))
        }

        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = listOf(summary)

        override suspend fun getThread(summary: ThreadSummary): Thread = Thread(
            id = summary.id,
            url = "https://example.com/thread/1",
            title = summary.title,
            posts = listOf(post),
        )

        companion object {
            val summary = ThreadSummary(
                sourceId = "test.source",
                boardUrl = "https://example.com/board",
                id = "thread-1",
                title = "private thread title",
                author = "author",
                createdAt = 1L,
                commentCount = 0,
                rawImage = null,
                thumbnail = null,
                previewContent = listOf(Paragraph.Text("private preview")),
            )
            val post = Post(
                id = "post-1",
                author = "author",
                createdAt = 1L,
                thumbnail = null,
                content = listOf(Paragraph.Text("private post body")),
                comments = emptyList(),
            )
        }
    }

    private class FakeAuthenticatedSource(
        private val sessionValid: Boolean = true,
    ) : AuthenticatedSource {
        var calls = 0
        override val sourceIdentity = SourceIdentity("test.extension", "a".repeat(64), "test.source")
        override val id = "test.source"
        override val name = "Authenticated test"
        override val language = "en"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override val authSpec: AuthSpec = AuthSpec.None
        override fun onAttach(runtime: SourceRuntime) = Unit
        override suspend fun validateSession(): Boolean { calls += 1; return sessionValid }
        override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
            calls += 1
            return BoardPage(listOf(Board(id, "https://example.com/board", "Board")))
        }
        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> {
            calls += 1
            return listOf(FakeSource.summary)
        }
        override suspend fun getThread(summary: ThreadSummary): Thread {
            calls += 1
            return Thread(
                id = summary.id,
                url = "https://example.com/thread/1",
                title = summary.title,
                posts = listOf(FakeSource.post),
            )
        }
    }
}
