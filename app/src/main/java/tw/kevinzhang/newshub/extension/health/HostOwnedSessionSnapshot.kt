package tw.kevinzhang.newshub.extension.health

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Strict, Host-only import format. Raw values must never be logged or included in reports. */
class HostOwnedSessionSnapshot private constructor(
    val sessions: List<Session>,
) {
    class Session internal constructor(
        val sourceId: String,
        val packageName: String,
        val signerSha256: String,
        val cookies: List<Cookie>,
    ) {
        override fun toString(): String = "HostOwnedSession(sourceId=$sourceId, cookieCount=${cookies.size})"
    }

    override fun toString(): String = "HostOwnedSessionSnapshot(sessionCount=${sessions.size})"

    companion object {
        const val MAX_BYTES = 64 * 1024
        private const val MAX_TTL_MS = 7 * 24 * 60 * 60 * 1_000L
        private const val CLOCK_SKEW_MS = 5 * 60 * 1_000L
        private val HEX_64 = Regex("[a-f0-9]{64}")
        private val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")
        private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")

        private data class Policy(
            val sourceId: String,
            val packageName: String,
            val origins: Set<String>,
            val parentDomains: Set<String>,
            val userAgentProfileId: String,
        )

        private val policies = listOf(
            Policy(
                sourceId = "tw.kevinzhang.eyny",
                packageName = "tw.kevinzhang.newshub.extension.eyny",
                origins = setOf(
                    "https://eyny.com",
                    "https://www.eyny.com",
                    "https://www52.eyny.com",
                    "https://www53.eyny.com",
                ),
                parentDomains = setOf("eyny.com"),
                userAgentProfileId = "eyny-android14-chrome120-v1",
            ),
            Policy(
                sourceId = "tw.kevinzhang.newshub.extension.gamer",
                packageName = "tw.kevinzhang.newshub.extension.gamer",
                origins = setOf(
                    "https://user.gamer.com.tw",
                    "https://forum.gamer.com.tw",
                    "https://www.gamer.com.tw",
                ),
                parentDomains = setOf("gamer.com.tw"),
                userAgentProfileId = "host-default-v1",
            ),
        ).associateBy(Policy::sourceId)

        fun decode(
            raw: String,
            nowEpochMs: Long = System.currentTimeMillis(),
            expectedSignerByPackage: Map<String, String>,
        ): HostOwnedSessionSnapshot {
            require(raw.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) { "Invalid session snapshot" }
            val root = runCatching { JsonParser.parseString(raw).asJsonObject }
                .getOrElse { throw IllegalArgumentException("Invalid session snapshot") }
                .only("schemaVersion", "sessions")
            require(root.integer("schemaVersion") == 1) { "Invalid session snapshot" }
            val elements = root.getAsJsonArray("sessions") ?: throw IllegalArgumentException("Invalid session snapshot")
            require(elements.size() in 1..2) { "Invalid session snapshot" }
            val seenSources = mutableSetOf<String>()
            val sessions = elements.map { element ->
                val value = element.asJsonObject.only(
                    "sourceId", "packageName", "signerSha256", "profileId",
                    "issuedAtEpochMs", "expiresAtEpochMs", "userAgentProfileId", "cookies",
                )
                val sourceId = value.string("sourceId")
                require(seenSources.add(sourceId)) { "Invalid session snapshot" }
                val policy = policies[sourceId] ?: throw IllegalArgumentException("Invalid session snapshot")
                require(value.string("packageName") == policy.packageName) { "Invalid session snapshot" }
                val signer = value.string("signerSha256")
                val expectedSigner = expectedSignerByPackage[policy.packageName]
                require(
                    signer.matches(HEX_64) && expectedSigner?.matches(HEX_64) == true && signer == expectedSigner,
                ) { "Invalid session snapshot" }
                require(value.string("profileId") == ExtensionHealthProfileSelection.FULL_PROFILE) {
                    "Invalid session snapshot"
                }
                require(value.string("userAgentProfileId") == policy.userAgentProfileId) {
                    "Invalid session snapshot"
                }
                val issuedAt = value.long("issuedAtEpochMs")
                val expiresAt = value.long("expiresAtEpochMs")
                require(issuedAt <= nowEpochMs + CLOCK_SKEW_MS) { "Invalid session snapshot" }
                require(expiresAt > nowEpochMs && expiresAt > issuedAt && expiresAt - issuedAt <= MAX_TTL_MS) {
                    "Invalid session snapshot"
                }
                val cookieElements = value.getAsJsonArray("cookies")
                    ?: throw IllegalArgumentException("Invalid session snapshot")
                require(cookieElements.size() in 1..64) { "Invalid session snapshot" }
                val identities = mutableSetOf<Triple<String, String, String>>()
                val cookies = cookieElements.map { cookieElement ->
                    val cookie = cookieElement.asJsonObject.only(
                        "origin", "name", "value", "domain", "path", "secure", "httpOnly",
                        "hostOnly", "expiresAtEpochMs",
                    )
                    val originText = cookie.string("origin")
                    require(originText in policy.origins) { "Invalid session snapshot" }
                    val origin = runCatching { originText.toHttpUrl() }.getOrNull()
                        ?: throw IllegalArgumentException("Invalid session snapshot")
                    require(origin.isHttps && origin.port == 443 && origin.encodedPath == "/") {
                        "Invalid session snapshot"
                    }
                    val name = cookie.string("name")
                    val rawValue = cookie.string("value")
                    val domain = cookie.string("domain").lowercase()
                    val path = cookie.string("path")
                    val hostOnly = cookie.boolean("hostOnly")
                    require(name.matches(COOKIE_NAME) && rawValue.length <= 4_096 && !CONTROL.containsMatchIn(rawValue)) {
                        "Invalid session snapshot"
                    }
                    require(path.startsWith('/') && path.length <= 512 && !CONTROL.containsMatchIn(path)) {
                        "Invalid session snapshot"
                    }
                    require(cookie.boolean("secure")) { "Invalid session snapshot" }
                    if (hostOnly) {
                        require(domain == origin.host) { "Invalid session snapshot" }
                    } else {
                        require(domain in policy.parentDomains && (origin.host == domain || origin.host.endsWith(".$domain"))) {
                            "Invalid session snapshot"
                        }
                    }
                    require(identities.add(Triple(name, domain, path))) { "Invalid session snapshot" }
                    val cookieExpiry = cookie.long("expiresAtEpochMs")
                    require(cookieExpiry > nowEpochMs) { "Invalid session snapshot" }
                    Cookie.Builder()
                        .name(name)
                        .value(rawValue)
                        .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
                        .path(path)
                        .secure()
                        .apply { if (cookie.boolean("httpOnly")) httpOnly() }
                        .expiresAt(minOf(cookieExpiry, expiresAt))
                        .build()
                }
                Session(sourceId, policy.packageName, signer, cookies)
            }
            return HostOwnedSessionSnapshot(sessions)
        }

        private fun JsonObject.only(vararg names: String): JsonObject = apply {
            require(keySet() == names.toSet()) { "Invalid session snapshot" }
        }

        private fun JsonObject.string(name: String): String =
            get(name)?.takeUnless { it.isJsonNull }?.asString ?: throw IllegalArgumentException("Invalid session snapshot")

        private fun JsonObject.integer(name: String): Int =
            runCatching { get(name).asInt }.getOrElse { throw IllegalArgumentException("Invalid session snapshot") }

        private fun JsonObject.long(name: String): Long =
            runCatching { get(name).asLong }.getOrElse { throw IllegalArgumentException("Invalid session snapshot") }

        private fun JsonObject.boolean(name: String): Boolean =
            runCatching { get(name).asBoolean }.getOrElse { throw IllegalArgumentException("Invalid session snapshot") }
    }
}
