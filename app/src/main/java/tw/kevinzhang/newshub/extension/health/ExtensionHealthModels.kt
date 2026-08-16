package tw.kevinzhang.newshub.extension.health

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.security.MessageDigest

/**
 * Host-owned instructions for one bounded live extension probe.
 *
 * Profiles are bundled with the trusted NewsHub test APK. They deliberately contain neither
 * credentials nor source-controlled login URLs.
 */
data class ExtensionHealthProfile(
    val schemaVersion: Int,
    val profileId: String,
    val operationTimeoutMs: Long,
    val runTimeoutMs: Long,
    val maxRequests: Int,
    val sources: List<SourceHealthProfile>,
) {
    fun validated(): ExtensionHealthProfile = apply {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported health profile schema" }
        require(profileId.matches(SAFE_ID)) { "Invalid profile id" }
        require(operationTimeoutMs in 1_000..30_000) { "Invalid operation timeout" }
        require(runTimeoutMs in operationTimeoutMs..600_000) { "Invalid run timeout" }
        require(maxRequests in 1..100) { "Invalid request budget" }
        require(sources.isNotEmpty() && sources.size <= 25) { "Invalid source count" }
        require(sources.map(SourceHealthProfile::sourceId).distinct().size == sources.size) {
            "Duplicate source id"
        }
        sources.forEach(SourceHealthProfile::validate)
        require(maxRequests >= sources.sumOf(SourceHealthProfile::maximumRequestCount)) {
            "Request budget cannot cover the configured probes"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

data class SourceHealthProfile(
    val sourceId: String,
    val packageName: String,
    val allowedHosts: Set<String>,
    val boardQuery: String = "",
    val boardNameContains: String? = null,
    val boardUrl: String? = null,
    val requireAuthenticatedSession: Boolean = false,
    val authenticatedOperations: Set<HealthProbeOperation> = emptySet(),
    val minimumSummaries: Int = 1,
    val minimumPosts: Int = 1,
) {
    internal fun validate() {
        require(sourceId.matches(SAFE_NAME)) { "Invalid source id" }
        require(packageName.matches(SAFE_NAME)) { "Invalid package name" }
        require(allowedHosts.isNotEmpty() && allowedHosts.size <= 10) { "Invalid host allowlist" }
        require(allowedHosts.all { it.matches(HOST) && it == it.lowercase() }) {
            "Hosts must be exact lower-case names"
        }
        require(boardQuery.length <= 100 && !boardQuery.contains(CONTROL_CHAR)) {
            "Invalid board query"
        }
        require(boardNameContains == null || boardNameContains.length in 1..100) {
            "Invalid board selector"
        }
        boardUrl?.let { requireHostAllowed(it, allowedHosts) }
        require(HealthProbeOperation.GET_BOARD_PAGE !in authenticatedOperations) {
            "Board discovery must remain available without a session"
        }
        require(
            HealthProbeOperation.GET_THREAD_SUMMARIES !in authenticatedOperations ||
                HealthProbeOperation.GET_THREAD_PAGE in authenticatedOperations,
        ) { "Authenticated probe operations must form a dependency-safe suffix" }
        require(minimumSummaries in 1..20) { "Invalid minimum summary count" }
        require(minimumPosts in 1..20) { "Invalid minimum post count" }
    }

    internal fun maximumRequestCount(): Int = if (requireAuthenticatedSession) {
        HealthProbeOperation.entries.size + 1
    } else {
        HealthProbeOperation.entries.size - authenticatedOperations.size
    }

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,159}")
        private val HOST = Regex("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")
        private val CONTROL_CHAR = Regex("[\\x00-\\x1f\\x7f]")
    }
}

enum class HealthProbeOperation(val wireName: String) {
    GET_BOARD_PAGE("get_board_page"),
    GET_THREAD_SUMMARIES("get_thread_summaries"),
    GET_THREAD_PAGE("get_thread_page");

    companion object {
        fun fromWireName(value: String): HealthProbeOperation = entries.firstOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("Unknown authenticated health operation")
    }
}

/** AUTH_PENDING is explicit incomplete evidence; it must never be treated as PASS. */
enum class HealthStatus { PASS, AUTH_PENDING, PARTIAL_AUTH_PENDING, FAIL }

enum class HealthFailureClass {
    AUTH_REQUIRED,
    RATE_LIMITED,
    SITE_UNAVAILABLE,
    PARSER_CONTRACT,
    HOST_POLICY,
    HOST_RUNTIME,
    TIMEOUT,
    PROFILE_INVALID,
    UNKNOWN,
}

data class ExtensionHealthReport(
    val schemaVersion: Int = 1,
    val profileId: String,
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
    val status: HealthStatus,
    val requestCount: Int,
    val results: List<SourceHealthResult>,
)

data class SourceHealthResult(
    val sourceId: String,
    val packageName: String,
    val status: HealthStatus,
    val durationMs: Long,
    val steps: List<HealthStepResult>,
    val evidenceScreenshot: String? = null,
)

data class HealthStepResult(
    val operation: String,
    val status: HealthStatus,
    val durationMs: Long,
    val observedCount: Int? = null,
    val failureClass: HealthFailureClass? = null,
    /** Stable grouping key. It is derived from safe identifiers, never exception text. */
    val failureFingerprint: String? = null,
    /** Safe policy evidence only; URL paths, queries, page content, and exception text are omitted. */
    val observedHost: String? = null,
    val allowedHosts: List<String>? = null,
)

internal class HostPolicyViolationException(
    val observedHost: String,
    val allowedHosts: Set<String>,
) : IllegalArgumentException("URL is outside the trusted host allowlist")

object ExtensionHealthJson {
    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    fun decodeProfile(json: String): ExtensionHealthProfile {
        val root = JsonParser.parseString(json).asJsonObject.requireOnly(
            "schemaVersion",
            "profileId",
            "operationTimeoutMs",
            "runTimeoutMs",
            "maxRequests",
            "sources",
        )
        return ExtensionHealthProfile(
            schemaVersion = root.requiredInt("schemaVersion"),
            profileId = root.requiredString("profileId"),
            operationTimeoutMs = root.requiredLong("operationTimeoutMs"),
            runTimeoutMs = root.requiredLong("runTimeoutMs"),
            maxRequests = root.requiredInt("maxRequests"),
            sources = root.getAsJsonArray("sources").map { sourceElement ->
                val source = sourceElement.asJsonObject.requireOnly(
                    "sourceId",
                    "packageName",
                    "allowedHosts",
                    "boardQuery",
                    "boardNameContains",
                    "boardUrl",
                    "requireAuthenticatedSession",
                    "authenticatedOperations",
                    "minimumSummaries",
                    "minimumPosts",
                )
                SourceHealthProfile(
                    sourceId = source.requiredString("sourceId"),
                    packageName = source.requiredString("packageName"),
                    allowedHosts = source.getAsJsonArray("allowedHosts").mapTo(linkedSetOf()) {
                        it.asString
                    },
                    boardQuery = source.optionalString("boardQuery") ?: "",
                    boardNameContains = source.optionalString("boardNameContains"),
                    boardUrl = source.optionalString("boardUrl"),
                    requireAuthenticatedSession = source.get("requireAuthenticatedSession")?.asBoolean ?: false,
                    authenticatedOperations = source.getAsJsonArray("authenticatedOperations")
                        ?.mapTo(linkedSetOf()) { HealthProbeOperation.fromWireName(it.asString) }
                        .orEmpty(),
                    minimumSummaries = source.get("minimumSummaries")?.asInt ?: 1,
                    minimumPosts = source.get("minimumPosts")?.asInt ?: 1,
                )
            },
        ).validated()
    }

    fun encodeReport(report: ExtensionHealthReport): String = gson.toJson(report)
}

private fun JsonObject.requireOnly(vararg allowedNames: String): JsonObject = apply {
    require(keySet().all { it in allowedNames }) { "Unknown profile field" }
}

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(get(name)?.takeUnless { it.isJsonNull }) { "Missing profile field" }.asString

private fun JsonObject.optionalString(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.requiredInt(name: String): Int =
    requireNotNull(get(name)?.takeUnless { it.isJsonNull }) { "Missing profile field" }.asInt

private fun JsonObject.requiredLong(name: String): Long =
    requireNotNull(get(name)?.takeUnless { it.isJsonNull }) { "Missing profile field" }.asLong

internal fun failureFingerprint(
    sourceId: String,
    operation: String,
    failureClass: HealthFailureClass,
    packageName: String,
): String {
    val safeInput = listOf(sourceId, operation, failureClass.name, packageName).joinToString("|")
    return MessageDigest.getInstance("SHA-256")
        .digest(safeInput.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(24)
}

internal fun requireHostAllowed(url: String, allowedHosts: Set<String>) {
    val parsed = runCatching { URI(url) }.getOrElse { throw IllegalArgumentException("Invalid URL") }
    val observedHost = parsed.host?.lowercase().orEmpty()
    if (parsed.scheme != "https" || observedHost !in allowedHosts) {
        throw HostPolicyViolationException(observedHost, allowedHosts)
    }
    require(parsed.userInfo == null) { "URL user-info is forbidden" }
}
