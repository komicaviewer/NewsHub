package tw.kevinzhang.extension_api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class SourceRuntimeDescriptorTest {
    @Test fun `service descriptor maps all Source flags without manifest inference`() {
        val descriptor = PlainSource().toRuntimeDescriptor()

        assertEquals(ExtensionProtocol.VERSION, descriptor.protocolVersion)
        assertEquals("example.plain", descriptor.sourceId)
        assertEquals("Plain", descriptor.name)
        assertEquals("en", descriptor.language)
        assertEquals(12, descriptor.sourceVersion)
        assertEquals("https://cdn.example.com/icon.png", descriptor.iconUrl)
        assertFalse(descriptor.supportsCommentPagination)
        assertTrue(descriptor.alwaysUseRawImage)
        assertFalse(descriptor.needsLogin)
        assertNull(descriptor.webCookieAuth)
        assertNull(descriptor.oauthAuth)
        assertTrue(descriptor.supportsThreadSummaryPages)
        assertNull(descriptor.webLoginUserAgent)
    }

    @Test fun `service descriptor maps only the generic OAuth registration reference`() {
        val descriptor = OAuthSource().toRuntimeDescriptor()
        val auth = requireNotNull(descriptor.oauthAuth)

        assertNull(descriptor.webCookieAuth)
        assertEquals("reddit", auth.providerId)
        assertEquals("reddit.installed.default", auth.clientRegistrationId)
        assertEquals(setOf("identity", "read", "mysubreddits"), auth.scopes)
    }

    @Test fun `service descriptor maps complete WebCookie spec and user agent`() {
        val descriptor = AuthSource().toRuntimeDescriptor()
        val auth = requireNotNull(descriptor.webCookieAuth)

        // Authentication capability is present even though this Source keeps public content usable.
        assertFalse(descriptor.needsLogin)
        assertEquals("https://login.example.com/sign-in", auth.loginUrl)
        assertEquals(setOf("login.example.com", "api.example.com"), auth.allowedHosts)
        assertEquals(setOf("https://login.example.com", "https://api.example.com"), auth.cookieOrigins)
        assertEquals(setOf("example.com"), auth.cookieDomains)
        assertFalse(auth.javaScriptEnabled)
        assertEquals("Example Browser/1.0", descriptor.webLoginUserAgent)
    }

    private open class PlainSource : Source {
        override val id = "example.plain"
        override val name = "Plain"
        override val language = "en"
        override val version = 12
        override val iconUrl = "https://cdn.example.com/icon.png"
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = true
        override val needsLogin = false
        override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList(), null)
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread(summary.id, null, null, emptyList())
    }

    private class AuthSource : PlainSource(), AuthenticatedSource, WebLoginUserAgentProvider {
        override val authSpec = AuthSpec.WebCookie(
            loginUrl = "https://login.example.com/sign-in",
            allowedHosts = setOf("login.example.com", "api.example.com"),
            cookieOrigins = setOf("https://login.example.com", "https://api.example.com"),
            cookieDomains = setOf("example.com"),
            javaScriptEnabled = false,
        )
        override val webLoginUserAgent = "Example Browser/1.0"
        override suspend fun validateSession() = true
    }

    private class OAuthSource : PlainSource(), AuthenticatedSource {
        override val authSpec = AuthSpec.OAuth(
            providerId = "reddit",
            clientRegistrationId = "reddit.installed.default",
            scopes = setOf("identity", "read", "mysubreddits"),
        )
        override suspend fun validateSession() = true
    }
}
