package tw.kevinzhang.marketplace

import com.google.gson.JsonParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tw.kevinzhang.extension_api.BUILTIN_REPOSITORY_DOMAIN_ID
import java.util.Locale
import java.util.UUID

enum class RepositoryTrustMode { BUILTIN_PINNED, USER_PINNED }

enum class RepositoryDomainState { ACTIVE, EXPIRED, SUSPENDED, REVOKED }

data class RepositoryTrustDomain(
    val id: String,
    val canonicalBaseUrl: String,
    val trustMode: RepositoryTrustMode,
    val state: RepositoryDomainState,
    val rootThreshold: Int,
    val rootKeyFingerprints: Set<String>,
) {
    init {
        require(UUID.fromString(id).toString() == id) { "Repository domain id must be a canonical UUID" }
        require(canonicalRepositoryBaseUrl(canonicalBaseUrl).toString().trimEnd('/') == canonicalBaseUrl) {
            "Repository base URL must be canonical"
        }
        require(rootThreshold in 1..rootKeyFingerprints.size) { "Invalid root signature threshold" }
        require(rootKeyFingerprints.all { it.matches(SHA256_PATTERN) }) { "Invalid root key fingerprint" }
    }

    val baseUrl: HttpUrl get() = "$canonicalBaseUrl/".toHttpUrlOrNull()
        ?: throw TrustedMetadataException("Invalid repository base URL")
}

data class RootTrustInspection(
    val threshold: Int,
    val keyFingerprints: Set<String>,
)

/**
 * A root that has been downloaded and self-verified, but has not been trusted or persisted yet.
 * [confirmationToken] is deliberately opaque and process-local: cancelling the confirmation can
 * therefore leave no repository id or trust files behind.
 */
data class RepositoryRootPreview(
    val confirmationToken: String,
    val canonicalBaseUrl: String,
    val rootThreshold: Int,
    val rootKeyFingerprints: Set<String>,
)

internal fun inspectTrustedRoot(rootBytes: ByteArray): RootTrustInspection {
    val root = runCatching { JsonParser.parseString(rootBytes.toString(Charsets.UTF_8)).asJsonObject }
        .getOrElse { throw TrustedMetadataException("Invalid root metadata", it) }
    val signed = root.getAsJsonObject("signed")
        ?: throw TrustedMetadataException("Root metadata has no signed body")
    val rootRole = signed.getAsJsonObject("roles")?.getAsJsonObject("root")
        ?: throw TrustedMetadataException("Root metadata has no root role")
    val threshold = rootRole.get("threshold")?.takeIf { it.isJsonPrimitive }?.asInt
        ?: throw TrustedMetadataException("Root metadata has no threshold")
    val keyIds = rootRole.getAsJsonArray("keyids")?.mapTo(linkedSetOf()) { element ->
        element.asString.lowercase(Locale.ROOT).also {
            if (!it.matches(SHA256_PATTERN)) throw TrustedMetadataException("Invalid root key fingerprint")
        }
    } ?: throw TrustedMetadataException("Root metadata has no key fingerprints")
    if (threshold !in 1..keyIds.size) throw TrustedMetadataException("Invalid root signature threshold")
    return RootTrustInspection(threshold, keyIds)
}

internal fun canonicalRepositoryBaseUrl(value: String): HttpUrl {
    val parsed = value.trim().toHttpUrlOrNull()
        ?: throw TrustedMetadataException("Invalid repository URL")
    if (parsed.scheme != "https" || parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
        parsed.query != null || parsed.fragment != null || parsed.port != 443
    ) {
        throw TrustedMetadataException("Repository URL must be a fixed HTTPS origin")
    }
    if (parsed.encodedPathSegments.any { it == "." || it == ".." }) {
        throw TrustedMetadataException("Repository URL contains path traversal")
    }
    return parsed.newBuilder()
        .host(parsed.host.lowercase(Locale.ROOT))
        .encodedPath(parsed.encodedPath.trimEnd('/').ifEmpty { "/" })
        .build()
}

object RepositoryTrustDomains {
    const val OFFICIAL_ID = BUILTIN_REPOSITORY_DOMAIN_ID
    const val OFFICIAL_BASE_URL = "https://raw.githubusercontent.com/komicaviewer/extensions/main"

    fun official(rootBytes: ByteArray): RepositoryTrustDomain {
        val inspection = inspectTrustedRoot(rootBytes)
        return RepositoryTrustDomain(
            id = OFFICIAL_ID,
            canonicalBaseUrl = OFFICIAL_BASE_URL,
            trustMode = RepositoryTrustMode.BUILTIN_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = inspection.threshold,
            rootKeyFingerprints = inspection.keyFingerprints,
        )
    }
}

private val SHA256_PATTERN = Regex("[a-f0-9]{64}")
