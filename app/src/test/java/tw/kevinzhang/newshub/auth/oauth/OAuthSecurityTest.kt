package tw.kevinzhang.newshub.auth.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthSecurityTest {
    @Test
    fun `state and verifier are high entropy url-safe values`() {
        val firstState = OAuthSecurity.newState()
        val secondState = OAuthSecurity.newState()
        val verifier = OAuthSecurity.newCodeVerifier()

        assertNotEquals(firstState, secondState)
        assertTrue(firstState.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(verifier.contains('='))
    }

    @Test
    fun `pkce S256 challenge matches RFC 7636 vector`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            OAuthSecurity.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `state comparison accepts only exact bytes`() {
        assertTrue(OAuthSecurity.constantTimeEquals("opaque-state", "opaque-state"))
        assertFalse(OAuthSecurity.constantTimeEquals("opaque-state", "opaque-state-x"))
        assertFalse(OAuthSecurity.constantTimeEquals("opaque-state", "opaque-statf"))
    }
}
