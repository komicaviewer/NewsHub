package tw.kevinzhang.newshub.auth.oauth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthCallbackValidationTest {
    private val expected = "tw.kevinzhang.newshub.oauth://callback/reddit"

    @Test
    fun `accepts exact redirect origin and path with provider query`() {
        assertTrue(
            oauthCallbackMatches(
                "$expected?state=opaque&code=authorization-code",
                expected,
            ),
        )
    }

    @Test
    fun `rejects redirect host path userinfo and fragment confusion`() {
        assertFalse(oauthCallbackMatches("tw.kevinzhang.newshub.oauth://evil/reddit?state=x", expected))
        assertFalse(oauthCallbackMatches("tw.kevinzhang.newshub.oauth://callback/facebook?state=x", expected))
        assertFalse(oauthCallbackMatches("tw.kevinzhang.newshub.oauth://user@callback/reddit?state=x", expected))
        assertFalse(oauthCallbackMatches("$expected?state=x#fragment", expected))
    }
}
