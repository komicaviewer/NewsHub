package tw.kevinzhang.extension_loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.OAuthAuthDescriptor
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceRuntimeDescriptor
import tw.kevinzhang.extension_api.WebCookieAuthDescriptor

class SourceRuntimeDescriptorValidatorTest {
    @Test fun `preserves flags icon complete authentication spec and optional user agent`() {
        val validated = validateSourceRuntimeDescriptor(validRuntime(), manifest(), policy())

        assertEquals(7, validated.sourceVersion)
        assertEquals("https://cdn.example.com/icon.png", validated.iconUrl)
        assertFalse(validated.supportsCommentPagination)
        assertTrue(validated.alwaysUseRawImage)
        assertFalse(validated.needsLogin)
        val auth = validated.authSpec as AuthSpec.WebCookie
        assertEquals("https://login.example.com/sign-in?next=%2F", auth.loginUrl)
        assertEquals(setOf("login.example.com", "api.example.com"), auth.allowedHosts)
        assertEquals(setOf("https://login.example.com", "https://api.example.com/"), auth.cookieOrigins)
        assertEquals(setOf("example.com"), auth.cookieDomains)
        assertFalse(auth.javaScriptEnabled)
        assertEquals("NewsHub Extension Browser/1.0", validated.webLoginUserAgent)
        assertFalse(validated.supportsThreadSummaryPages)
    }

    @Test fun `validates provider neutral OAuth registration against bounded identifiers and scopes`() {
        val runtime = validRuntime().copy(
            webCookieAuth = null,
            webLoginUserAgent = null,
            oauthAuth = OAuthAuthDescriptor(
                providerId = "reddit",
                clientRegistrationId = "reddit.installed.default",
                scopes = setOf("identity", "read", "mysubreddits"),
            ),
            supportsThreadSummaryPages = true,
        )

        val validated = validateSourceRuntimeDescriptor(runtime, manifest(), policy())

        assertEquals(
            AuthSpec.OAuth(
                "reddit",
                "reddit.installed.default",
                setOf("identity", "read", "mysubreddits"),
            ),
            validated.authSpec,
        )
        assertTrue(validated.supportsThreadSummaryPages)
    }

    @Test fun `rejects identity version and authentication shape drift`() {
        val valid = validRuntime()
        listOf(
            valid.copy(protocolVersion = 1),
            valid.copy(sourceId = "other.source"),
            valid.copy(name = "Other"),
            valid.copy(language = "zh-TW"),
            valid.copy(sourceVersion = 0),
            valid.copy(needsLogin = true, webCookieAuth = null, webLoginUserAgent = null),
        ).forEach(::assertInvalid)
    }

    @Test fun `rejects URLs hosts origins domains and user agents outside canonical signed surface`() {
        val auth = requireNotNull(validRuntime().webCookieAuth)
        listOf(
            validRuntime().copy(iconUrl = "https://evil.example/icon.png"),
            validRuntime().copy(iconUrl = "http://cdn.example.com/icon.png"),
            validRuntime().copy(webCookieAuth = auth.copy(loginUrl = "https://evil.example/sign-in")),
            validRuntime().copy(webCookieAuth = auth.copy(allowedHosts = auth.allowedHosts + "evil.example")),
            validRuntime().copy(webCookieAuth = auth.copy(allowedHosts = setOf("Login.example.com"))),
            validRuntime().copy(webCookieAuth = auth.copy(cookieOrigins = setOf("https://login.example.com/path"))),
            validRuntime().copy(webCookieAuth = auth.copy(cookieOrigins = setOf("https://login.example.com?query=1"))),
            validRuntime().copy(webCookieAuth = auth.copy(cookieDomains = setOf("evil.example"))),
            validRuntime().copy(webLoginUserAgent = "valid\r\nInjected: yes"),
            validRuntime().copy(webLoginUserAgent = "x".repeat(513)),
        ).forEach(::assertInvalid)
    }

    @Test fun `rejects user agent without authentication`() {
        assertInvalid(
            validRuntime().copy(
                needsLogin = false,
                webCookieAuth = null,
                webLoginUserAgent = "Browser/1.0",
            ),
        )
    }

    @Test fun `rejects ambiguous or unbounded OAuth descriptors`() {
        val validOAuth = OAuthAuthDescriptor(
            providerId = "reddit",
            clientRegistrationId = "reddit.installed.default",
            scopes = setOf("identity", "read"),
        )
        listOf(
            validRuntime().copy(oauthAuth = validOAuth),
            validRuntime().copy(
                webCookieAuth = null,
                webLoginUserAgent = null,
                oauthAuth = validOAuth.copy(providerId = "Reddit"),
            ),
            validRuntime().copy(
                webCookieAuth = null,
                webLoginUserAgent = null,
                oauthAuth = validOAuth.copy(clientRegistrationId = "bad registration"),
            ),
            validRuntime().copy(
                webCookieAuth = null,
                webLoginUserAgent = null,
                oauthAuth = validOAuth.copy(scopes = emptySet()),
            ),
            validRuntime().copy(
                webCookieAuth = null,
                webLoginUserAgent = null,
                oauthAuth = validOAuth.copy(scopes = setOf("read scope")),
            ),
            validRuntime().copy(
                webCookieAuth = null,
                oauthAuth = validOAuth,
                webLoginUserAgent = "Browser/1.0",
            ),
        ).forEach(::assertInvalid)
    }

    private fun assertInvalid(runtime: SourceRuntimeDescriptor) {
        assertTrue(runCatching { validateSourceRuntimeDescriptor(runtime, manifest(), policy()) }.isFailure)
    }

    private fun validRuntime() = SourceRuntimeDescriptor(
        protocolVersion = ExtensionProtocol.VERSION,
        sourceId = "example.source",
        name = "Example",
        language = "en",
        sourceVersion = 7,
        iconUrl = "https://cdn.example.com/icon.png",
        supportsCommentPagination = false,
        alwaysUseRawImage = true,
        needsLogin = false,
        webCookieAuth = WebCookieAuthDescriptor(
            loginUrl = "https://login.example.com/sign-in?next=%2F",
            allowedHosts = setOf("login.example.com", "api.example.com"),
            cookieOrigins = setOf("https://login.example.com", "https://api.example.com/"),
            cookieDomains = setOf("example.com"),
            javaScriptEnabled = false,
        ),
        webLoginUserAgent = "NewsHub Extension Browser/1.0",
    )

    private fun manifest() = ExtensionDescriptor(
        packageName = "org.example.extension",
        serviceClassName = "org.example.extension.SourceService",
        processName = "org.example.extension:source",
        sourceId = "example.source",
        name = "Example",
        lang = "en",
        baseUrl = "https://api.example.com",
        protocol = ExtensionProtocol.VERSION,
    )

    private fun policy(): SourceNetworkPolicy {
        val operation = NetworkOperationPolicy(
            name = NetworkOperations.SOURCE_READ,
            methods = setOf("GET"),
            pathPrefixes = setOf("/"),
        )
        return SourceNetworkPolicy(
            exactHosts = setOf("api.example.com"),
            operations = mapOf(NetworkOperations.SOURCE_READ to operation),
            resourceExactHosts = setOf("cdn.example.com"),
            externalExactHosts = emptySet(),
            authExactHosts = setOf("login.example.com", "api.example.com"),
        )
    }
}
