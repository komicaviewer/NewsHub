package tw.kevinzhang.newshub.github

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubFailure(val status: Int = 0, val kind: String = "network") : IOException(
    when {
        status == 401 -> "GitHub 授權已失效，請重新登入。"
        kind == "rate_limit" -> "GitHub 請求次數受限，請稍後重試。"
        status == 404 -> "找不到 repository 或目前帳號無權存取。"
        status == 403 -> "GitHub 拒絕存取，請檢查組織的 OAuth／SSO 授權政策。"
        kind == "access_denied" -> "已取消 GitHub 授權。"
        kind == "expired_token" -> "GitHub 授權碼已到期，請重新登入。"
        else -> "無法完成 GitHub 請求，請稍後再試。"
    })

/** Dedicated client: never inherit HTTP logging or source interceptors for OAuth request bodies. */
class GitHubHttp(base: OkHttpClient) {
    private val client = base.newBuilder().apply {
        // Keep application interceptors supplied by tests; production is a dedicated plain client.
        networkInterceptors().clear()
    }.followRedirects(false).followSslRedirects(false).cache(null)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun json(request: Request, credential: RepositoryAccessCredential? = null): Pair<JsonElement, Boolean> {
        require(request.url.scheme == "https" && request.url.host in setOf("github.com", "api.github.com"))
        credential?.ensureCurrent()
        val call = client.newCall(request)
        val lease = credential?.lifetime?.invokeOnCompletion { call.cancel() }
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                suspendCancellableCoroutine<Response> { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { continuation.resumeWithException(GitHubFailure()) }
                        override fun onResponse(call: Call, response: Response) {
                            continuation.resume(response) { response.close() }
                        }
                    })
                }.use { response ->
                    credential?.ensureCurrent()
                    if (!response.isSuccessful) throw GitHubFailure(response.code,
                        if (response.header("X-RateLimit-Remaining") == "0" || response.code == 429 || response.header("Retry-After") != null) "rate_limit" else "http")
                    val body = response.body ?: throw GitHubFailure()
                    if (body.contentLength() > 2_000_000) throw GitHubFailure()
                    val bytes = body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 2_000_000) throw GitHubFailure()
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > 2_000_000) throw GitHubFailure()
                    val json = try { JsonParser.parseString(bytes.toString(Charsets.UTF_8)) } catch (_: Exception) { throw GitHubFailure() }
                    credential?.ensureCurrent()
                    json to (response.header("Link")?.contains("rel=\"next\"") == true)
                }
            }
        } finally { lease?.dispose() }
    }

    suspend fun get(path: String, credential: RepositoryAccessCredential): Pair<JsonElement, Boolean> =
        json(Request.Builder().url("https://api.github.com/$path")
            .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2026-03-10")
            .apply { credential.withSecret { header("Authorization", "Bearer $it") } }.build(), credential)
}
