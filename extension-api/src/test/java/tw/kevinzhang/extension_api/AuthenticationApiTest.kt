package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationApiTest {
    @Test
    fun `web cookie spec preserves explicit host and origin boundaries`() {
        val spec = AuthSpec.WebCookie(
            loginUrl = "https://user.gamer.com.tw/login.php",
            allowedHosts = setOf("user.gamer.com.tw", "forum.gamer.com.tw"),
            cookieOrigins = setOf("https://user.gamer.com.tw", "https://forum.gamer.com.tw"),
            cookieDomains = setOf("gamer.com.tw"),
        )

        assertEquals("https://user.gamer.com.tw/login.php", spec.loginUrl)
        assertTrue("gamer.com.tw" !in spec.allowedHosts)
        assertTrue("https://gamer.com.tw" !in spec.cookieOrigins)
        assertEquals(setOf("gamer.com.tw"), spec.cookieDomains)
    }

    @Test
    fun `auth required exception defaults to a foreground user action`() {
        assertTrue(AuthenticationRequiredException().isUserAction)
    }

    @Test
    fun `oauth spec names a host-owned provider registration without carrying endpoints or tokens`() {
        val spec = AuthSpec.OAuth(
            providerId = "reddit",
            clientRegistrationId = "reddit.installed.default",
            scopes = setOf("identity", "read", "mysubreddits"),
        )

        assertEquals("reddit", spec.providerId)
        assertEquals("reddit.installed.default", spec.clientRegistrationId)
        assertEquals(setOf("identity", "read", "mysubreddits"), spec.scopes)
        assertEquals(
            setOf("providerId", "clientRegistrationId", "scopes"),
            AuthSpec.OAuth::class.java.declaredFields.mapTo(linkedSetOf()) { it.name },
        )
    }

    @Test
    fun `web login user agent is an optional source capability`() {
        val provider = object : WebLoginUserAgentProvider {
            override val webLoginUserAgent = "NewsHub test browser"
        }

        assertEquals("NewsHub test browser", provider.webLoginUserAgent)
    }
}
