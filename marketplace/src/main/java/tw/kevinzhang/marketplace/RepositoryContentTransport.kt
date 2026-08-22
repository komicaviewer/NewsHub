package tw.kevinzhang.marketplace

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy

/** Maps trusted virtual repository URLs to a bounded public-HTTPS or GitHub Contents request. */
internal class RepositoryContentTransport(
    baseClient: OkHttpClient,
    private val canonicalBaseUrl: String,
    private val access: RepositoryAccessDescriptor,
    private val domainId: String?,
    private val credentialProvider: suspend () -> RepositoryAccessCredential?,
) {
    private val repositoryBaseUrl = "$canonicalBaseUrl/".toHttpUrl()
    private val githubCoordinates = access.kind.takeIf { it == RepositoryAccessKind.GITHUB_CONTENTS }
        ?.let { requireGithubRepositoryBaseUrl(canonicalBaseUrl) }
    private val client = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .proxy(Proxy.NO_PROXY)
        .cache(null)
        .build()

    suspend fun fetchRequired(virtualUrl: HttpUrl, maxBytes: Long): ByteArray =
        fetch(virtualUrl, maxBytes, optional = false)
            ?: throw IOException("Repository response is empty")

    suspend fun fetchOptional(virtualUrl: HttpUrl, maxBytes: Long): ByteArray? =
        fetch(virtualUrl, maxBytes, optional = true)

    private suspend fun fetch(virtualUrl: HttpUrl, maxBytes: Long, optional: Boolean): ByteArray? {
        if (!isWithinRepositoryBase(repositoryBaseUrl, virtualUrl)) {
            throw TrustedMetadataException("Repository URL escaped its trust domain")
        }
        require(maxBytes >= 0) { "Negative repository response limit" }
        val request = when (access.kind) {
            RepositoryAccessKind.PUBLIC_HTTPS -> Request.Builder().url(virtualUrl).get().build()
            RepositoryAccessKind.GITHUB_CONTENTS -> githubRequest(
                virtualUrl,
                credentialProvider() ?: throw RepositoryAccessRequiredException(
                    domainId,
                    RepositoryAccessFailureReason.MISSING_CREDENTIAL,
                ),
            )
        }
        return client.newCall(request).execute().use { response ->
            if (response.request.url != request.url || response.isRedirect) {
                throw TrustedMetadataException("Repository request URL changed")
            }
            if (access.kind == RepositoryAccessKind.GITHUB_CONTENTS && response.code in setOf(401, 403)) {
                throw RepositoryAccessRequiredException(
                    domainId,
                    RepositoryAccessFailureReason.CREDENTIAL_REJECTED,
                )
            }
            if (optional && response.code == 404) return@use null
            if (access.kind == RepositoryAccessKind.GITHUB_CONTENTS && response.code == 404) {
                throw RepositoryAccessRequiredException(
                    domainId,
                    RepositoryAccessFailureReason.NOT_FOUND_OR_INACCESSIBLE,
                )
            }
            if (!response.isSuccessful) {
                throw IOException("Repository fetch failed: HTTP ${response.code}")
            }
            val declaredLength = response.body?.contentLength() ?: -1L
            if (declaredLength > maxBytes) {
                throw TrustedMetadataException("Repository response exceeds size limit")
            }
            response.body?.boundedRepositoryBytes(maxBytes)
                ?: throw IOException("Repository response is empty")
        }
    }

    private fun githubRequest(
        virtualUrl: HttpUrl,
        credential: RepositoryAccessCredential,
    ): Request {
        val coordinates = requireNotNull(githubCoordinates)
        val baseSegments = repositoryBaseUrl.pathSegments.filter(String::isNotEmpty)
        val requestedSegments = virtualUrl.pathSegments.filter(String::isNotEmpty)
        val relativeSegments = requestedSegments.drop(baseSegments.size)
        if (relativeSegments.isEmpty() || relativeSegments.any { it.isEmpty() || it == "." || it == ".." }) {
            throw TrustedMetadataException("Repository content path is invalid")
        }
        val apiUrl = GITHUB_API_BASE.newBuilder()
            .addPathSegment("repos")
            .addPathSegment(coordinates.owner)
            .addPathSegment(coordinates.repository)
            .addPathSegment("contents")
            .apply { relativeSegments.forEach(::addPathSegment) }
            .addQueryParameter("ref", requireNotNull(access.revision))
            .build()
        return Request.Builder()
            .url(apiUrl)
            .header("Accept", GITHUB_RAW_ACCEPT)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .apply {
                credential.withSecret { token -> header("Authorization", "Bearer $token") }
            }
            .get()
            .build()
    }

    private companion object {
        val GITHUB_API_BASE = "https://api.github.com/".toHttpUrl()
        const val GITHUB_RAW_ACCEPT = "application/vnd.github.raw+json"
        const val GITHUB_API_VERSION = "2026-03-10"
    }
}

private fun okhttp3.ResponseBody.boundedRepositoryBytes(limit: Long): ByteArray {
    byteStream().use { input ->
        val output = ByteArrayOutputStream(minOf(limit, 8192L).toInt())
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw TrustedMetadataException("Repository response exceeds size limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
