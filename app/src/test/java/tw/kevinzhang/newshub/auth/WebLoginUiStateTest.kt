package tw.kevinzhang.newshub.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.WebLoginUserAgentProvider

class WebLoginUiStateTest {

    private val request = WebLoginRequest(
        sourceId = "source-id",
        spec = AuthSpec.WebCookie(
            loginUrl = "https://example.com/login",
            allowedHosts = setOf("example.com"),
            cookieOrigins = setOf("https://example.com"),
        ),
    )

    @Test
    fun `verification retains request and clears a previous error`() {
        val failed = WebLoginStateReducer.fail(
            WebLoginStateReducer.begin(request),
            "登入驗證時發生問題，請稍後再試一次。",
        )

        val verifying = WebLoginStateReducer.beginVerification(failed)

        assertEquals(request, verifying.request)
        assertEquals(WebLoginPhase.Verifying, verifying.phase)
        assertTrue(verifying.isVerifying)
        assertNull(verifying.errorMessage)
    }

    @Test
    fun `failed verification preserves web page request for retry`() {
        val failed = WebLoginStateReducer.fail(
            WebLoginStateReducer.begin(request),
            "登入未完成或登入資訊已失效，請確認後再試一次。",
        )

        assertEquals(request, failed.request)
        assertEquals(WebLoginPhase.Browsing, failed.phase)
        assertFalse(failed.isVerifying)
        assertEquals("登入未完成或登入資訊已失效，請確認後再試一次。", failed.errorMessage)
    }

    @Test
    fun `clearing state removes the active request`() {
        val cleared = WebLoginStateReducer.clear()

        assertNull(cleared.request)
        assertEquals(WebLoginPhase.Idle, cleared.phase)
        assertFalse(cleared.isVerifying)
        assertNull(cleared.errorMessage)
    }

    @Test
    fun `valid optional web login user agent is preserved exactly`() {
        val source = object : WebLoginUserAgentProvider {
            override val webLoginUserAgent = "Mozilla/5.0 NewsHub exact UA"
        }

        assertEquals("Mozilla/5.0 NewsHub exact UA", source.webLoginUserAgentOrNull())
        assertNull(Any().webLoginUserAgentOrNull())
    }

    @Test
    fun `web login user agent rejects header injection and unreasonable length`() {
        fun provider(value: String) = object : WebLoginUserAgentProvider {
            override val webLoginUserAgent = value
        }

        assertNull(provider("valid-looking\r\nInjected: value").webLoginUserAgentOrNull())
        assertNull(provider("x".repeat(513)).webLoginUserAgentOrNull())
    }
}
