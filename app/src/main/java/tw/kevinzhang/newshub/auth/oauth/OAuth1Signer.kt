package tw.kevinzhang.newshub.auth.oauth

import okhttp3.HttpUrl
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val OAUTH1_UNRESERVED =
    ('a'..'z').toSet() + ('A'..'Z') + ('0'..'9') + setOf('-', '.', '_', '~')

/** RFC 5849 HMAC-SHA1 signer. Callers inject nonce/time sources in deterministic tests. */
class OAuth1Signer(
    private val nonce: () -> String = ::secureOAuth1Nonce,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    fun authorizationHeader(
        method: String,
        url: HttpUrl,
        consumerKey: String,
        consumerSecret: String,
        token: String? = null,
        tokenSecret: String? = null,
        additionalOAuthParameters: Map<String, String> = emptyMap(),
    ): String {
        require(method == method.uppercase() && method.matches(Regex("[A-Z]{3,10}")))
        require(consumerKey.isNotBlank() && consumerSecret.isNotBlank())
        require(token == null || token.isNotBlank())
        require(tokenSecret == null || token != null)

        val oauth = linkedMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to nonce().also { require(it.matches(Regex("[A-Za-z0-9_-]{16,128}"))) },
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to epochSeconds().toString(),
            "oauth_version" to "1.0",
        ).apply {
            token?.let { put("oauth_token", it) }
            additionalOAuthParameters.forEach { (key, value) ->
                require(key.startsWith("oauth_") && key != "oauth_signature")
                require(value.isNotBlank() && '\r' !in value && '\n' !in value)
                put(key, value)
            }
        }
        val normalized = buildList {
            for (index in 0 until url.querySize) {
                add(
                    oauth1PercentEncode(url.queryParameterName(index)) to
                        oauth1PercentEncode(url.queryParameterValue(index).orEmpty()),
                )
            }
            oauth.forEach { (key, value) -> add(oauth1PercentEncode(key) to oauth1PercentEncode(value)) }
        }.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
            .joinToString("&") { (key, value) -> "$key=$value" }
        val baseUrl = buildString {
            append(url.scheme.lowercase())
            append("://")
            append(url.host.lowercase())
            val defaultPort = if (url.isHttps) 443 else 80
            if (url.port != defaultPort) append(':').append(url.port)
            append(url.encodedPath.ifEmpty { "/" })
        }
        val signatureBase = listOf(method, baseUrl, normalized)
            .joinToString("&", transform = ::oauth1PercentEncode)
        val signingKey = "${oauth1PercentEncode(consumerSecret)}&${oauth1PercentEncode(tokenSecret.orEmpty())}"
        val mac = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(signingKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA1"))
        }
        val signature = Base64.getEncoder().encodeToString(
            mac.doFinal(signatureBase.toByteArray(StandardCharsets.UTF_8)),
        )
        return (oauth + ("oauth_signature" to signature)).entries
            .sortedBy(Map.Entry<String, String>::key)
            .joinToString(", ", prefix = "OAuth ") { (key, value) ->
                "${oauth1PercentEncode(key)}=\"${oauth1PercentEncode(value)}\""
            }
    }
}

internal fun oauth1PercentEncode(value: String): String = buildString {
    value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if (character in OAUTH1_UNRESERVED) {
            append(character)
        } else {
            append('%')
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }
}

private fun secureOAuth1Nonce(): String {
    val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private const val HEX = "0123456789ABCDEF"
