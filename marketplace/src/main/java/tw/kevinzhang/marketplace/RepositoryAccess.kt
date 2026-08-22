package tw.kevinzhang.marketplace

import okhttp3.HttpUrl
import java.io.IOException

/** The non-secret transport configuration that is safe to persist with a trust domain. */
enum class RepositoryAccessKind { PUBLIC_HTTPS, GITHUB_CONTENTS }

data class RepositoryAccessDescriptor(
    val kind: RepositoryAccessKind = RepositoryAccessKind.PUBLIC_HTTPS,
    val revision: String? = null,
) {
    init {
        when (kind) {
            RepositoryAccessKind.PUBLIC_HTTPS -> require(revision == null) {
                "A public HTTPS repository cannot declare a GitHub revision"
            }
            RepositoryAccessKind.GITHUB_CONTENTS -> require(isSafeGitRevision(revision)) {
                "A GitHub repository requires a safe revision"
            }
        }
    }

    companion object {
        fun publicHttps() = RepositoryAccessDescriptor()

        fun githubContents(revision: String = "main") = RepositoryAccessDescriptor(
            kind = RepositoryAccessKind.GITHUB_CONTENTS,
            revision = revision,
        )
    }
}

/** Non-secret input used while inspecting a repository. Credentials are supplied separately. */
data class RepositoryAccessDraft(
    val repositoryUrl: String,
    val access: RepositoryAccessDescriptor = RepositoryAccessDescriptor.publicHttps(),
)

/**
 * A short-lived in-process credential. It intentionally has no value equality and its string
 * representation is always redacted so accidental exception interpolation cannot disclose it.
 */
class RepositoryAccessCredential private constructor(internal val secret: String) {
    init {
        require(secret.isNotBlank() && secret.length <= MAX_CREDENTIAL_LENGTH &&
            secret.none { it.code < 0x20 || it.code == 0x7f }
        ) { "Invalid repository credential" }
    }

    override fun toString(): String = "RepositoryAccessCredential(REDACTED)"

    /** Keep raw access scoped to the secure-store or HTTP-header operation. */
    fun <T> withSecret(block: (String) -> T): T = block(secret)

    companion object {
        private const val MAX_CREDENTIAL_LENGTH = 4096

        fun githubToken(token: String) = RepositoryAccessCredential(token)
    }
}

interface RepositoryCredentialProvider {
    suspend fun getCredential(domainId: String): RepositoryAccessCredential?
}

interface RepositoryCredentialStore : RepositoryCredentialProvider {
    suspend fun saveCredential(domainId: String, credential: RepositoryAccessCredential)
    suspend fun deleteCredential(domainId: String)
}

enum class RepositoryAccessFailureReason {
    MISSING_CREDENTIAL,
    CREDENTIAL_REJECTED,
    NOT_FOUND_OR_INACCESSIBLE,
}

class RepositoryAccessRequiredException(
    val domainId: String?,
    val reason: RepositoryAccessFailureReason,
) : IOException(
    when (reason) {
        RepositoryAccessFailureReason.MISSING_CREDENTIAL -> "Repository authorization is required"
        RepositoryAccessFailureReason.CREDENTIAL_REJECTED -> "Repository authorization was rejected"
        RepositoryAccessFailureReason.NOT_FOUND_OR_INACCESSIBLE ->
            "Repository content was not found or is inaccessible"
    },
)

private fun isSafeGitRevision(value: String?): Boolean =
    value != null && value.length in 1..255 && value != "." && value != ".." &&
        !value.startsWith('/') && !value.endsWith('/') && !value.contains("//") &&
        value.split('/').none { it == "." || it == ".." } &&
        value.none { it.code < 0x20 || it.code == 0x7f || it == '?' || it == '#' || it == '\\' }

internal data class GitHubRepositoryCoordinates(val owner: String, val repository: String)

internal fun requireGithubRepositoryBaseUrl(value: String): GitHubRepositoryCoordinates {
    val url = canonicalRepositoryBaseUrl(value)
    val segments = url.pathSegments.filter(String::isNotEmpty)
    if (url.host != GITHUB_WEB_HOST || segments.size != 2 ||
        !segments[0].matches(GITHUB_OWNER_PATTERN) || !segments[1].matches(GITHUB_REPOSITORY_PATTERN)
    ) {
        throw TrustedMetadataException("GitHub repository URL must identify exactly one repository")
    }
    return GitHubRepositoryCoordinates(segments[0], segments[1])
}

internal fun isWithinRepositoryBase(base: HttpUrl, requested: HttpUrl): Boolean =
    requested.scheme == base.scheme && requested.host == base.host && requested.port == base.port &&
        base.encodedPath.trimEnd('/').let { basePath ->
            requested.encodedPath == basePath || requested.encodedPath.startsWith("$basePath/")
        }

private const val GITHUB_WEB_HOST = "github.com"
private val GITHUB_OWNER_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
private val GITHUB_REPOSITORY_PATTERN = Regex("[A-Za-z0-9._-]{1,100}")
