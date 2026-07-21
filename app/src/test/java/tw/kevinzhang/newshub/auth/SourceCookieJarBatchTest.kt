package tw.kevinzhang.newshub.auth

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCookieJarBatchTest {

    @Test
    fun `WebView cookies from multiple origins are dispatched in one batch and remain host only`() {
        val firstOrigin = "https://forum.eyny.com/login".toHttpUrl()
        val secondOrigin = "https://blog.eyny.com/login".toHttpUrl()
        var dispatchCount = 0
        var dispatchedCookies: List<Cookie> = emptyList()

        saveWebViewCookieBatch(
            listOf(firstOrigin to "forum_session=first", secondOrigin to "blog_session=second"),
        ) { cookies ->
            dispatchCount += 1
            dispatchedCookies = cookies
        }

        assertEquals(1, dispatchCount)
        assertEquals(2, dispatchedCookies.size)
        assertTrue(dispatchedCookies.all { it.hostOnly })
        assertTrue(dispatchedCookies.first { it.name == "forum_session" }.matches(firstOrigin))
        assertFalse(
            dispatchedCookies.first { it.name == "forum_session" }
                .matches("https://sub.forum.eyny.com/".toHttpUrl()),
        )
    }

    @Test
    fun `one hundred and one origin headers still use one persistence dispatch`() {
        val originHeaders = (0..100).map { index ->
            "https://origin$index.eyny.com/".toHttpUrl() to "session$index=value$index"
        }
        var dispatchCount = 0
        var cookieCount = 0

        saveWebViewCookieBatch(originHeaders) { cookies ->
            dispatchCount += 1
            cookieCount = cookies.size
        }

        assertEquals(1, dispatchCount)
        assertEquals(101, cookieCount)
    }

    @Test
    fun `batch merge replaces only the same cookie identity and preserves distinct paths`() {
        val origin = "https://forum.eyny.com/".toHttpUrl()
        val rootCookie = Cookie.parse(origin, "session=old")!!
        val accountCookie = Cookie.parse(origin, "session=account; Path=/account")!!
        val replacement = Cookie.parse(origin, "session=new")!!

        val merged = mergeCookieBatch(
            existingCookies = listOf(rootCookie, accountCookie),
            incomingCookies = listOf(replacement),
        )

        assertEquals(2, merged.size)
        assertEquals("new", merged.single { it.path == "/" }.value)
        assertEquals("account", merged.single { it.path == "/account" }.value)
        assertTrue(merged.all { it.hostOnly })
    }
}
