package tw.kevinzhang.newshub.auth.oauth

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_OAUTH_RESPONSE_CHARS = 64 * 1_024

@Singleton
class OAuthHttpClient @Inject constructor(
    baseClient: OkHttpClient,
) {
    private val client = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cache(null)
        .build()

    fun exchange(request: Request): String = client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (body.length > MAX_OAUTH_RESPONSE_CHARS) throw IOException("OAuth response exceeded limit")
        if (response.code !in 200..299) throw IOException("OAuth token endpoint rejected the request")
        body
    }
}
