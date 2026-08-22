package tw.kevinzhang.newshub.auth.oauth

import android.net.Uri
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import tw.kevinzhang.extension_api.AuthSpec

class OAuthProviderRegistryTest {
    private val adapter = object : OAuthProviderAdapter {
        override val providerId = "future_provider"
        override val allowedScopes = setOf("read", "identity")
        override val apiExactHosts = setOf("api.example.com")
        override fun registration(registrationId: String) =
            OAuthClientRegistration(registrationId, "public-client", "example.oauth://callback/provider")
                .takeIf { registrationId == "mobile-app" }
        override fun authorizationUri(
            registration: OAuthClientRegistration,
            scopes: Set<String>,
            state: String,
            codeChallenge: String,
        ): Uri = throw UnsupportedOperationException()
        override fun tokenExchangeRequest(
            registration: OAuthClientRegistration,
            code: String,
            codeVerifier: String,
        ): Request = throw UnsupportedOperationException()
        override fun tokenRefreshRequest(
            registration: OAuthClientRegistration,
            refreshToken: String,
        ): Request = throw UnsupportedOperationException()
        override fun parseTokenResponse(
            responseBody: String,
            requestedScopes: Set<String>,
            previousRefreshToken: String?,
            nowEpochMillis: Long,
        ): OAuthTokenSet = throw UnsupportedOperationException()
    }

    @Test
    fun `resolves only host registered provider registration and scopes`() {
        val resolved = OAuthProviderRegistry(setOf(adapter)).resolve(
            AuthSpec.OAuth("future_provider", "mobile-app", setOf("read")),
        )

        assertEquals("future_provider", resolved.adapter.providerId)
        assertEquals("mobile-app", resolved.registration.id)
        assertEquals(setOf("read"), resolved.scopes)
    }

    @Test
    fun `rejects unknown registration and scope escalation`() {
        val registry = OAuthProviderRegistry(setOf(adapter))

        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(AuthSpec.OAuth("future_provider", "unknown", setOf("read")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            registry.resolve(AuthSpec.OAuth("future_provider", "mobile-app", setOf("write")))
        }
    }
}
