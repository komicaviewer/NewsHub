package tw.kevinzhang.newshub.auth.oauth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object OAuthSecurity {
    private val secureRandom = SecureRandom()

    fun newState(): String = randomUrlSafe(32)

    fun newCodeVerifier(): String = randomUrlSafe(64)

    fun codeChallenge(verifier: String): String {
        require(verifier.length in 43..128) { "Invalid PKCE verifier" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return urlSafe(digest)
    }

    fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII),
        right.toByteArray(StandardCharsets.US_ASCII),
    )

    private fun randomUrlSafe(size: Int): String = urlSafe(ByteArray(size).also(secureRandom::nextBytes))

    private fun urlSafe(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
