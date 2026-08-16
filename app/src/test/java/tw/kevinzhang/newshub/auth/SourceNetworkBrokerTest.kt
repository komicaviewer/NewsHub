package tw.kevinzhang.newshub.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.NetworkRequestRule
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceNetworkResponse
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_api.SourceFailureException
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.ExtensionWireJson
import tw.kevinzhang.extension_api.EynyChallengeProof
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SourceNetworkBrokerTest {
    private val policy = SourceNetworkPolicy(
        exactHosts = setOf("news.example"),
        operations = emptyMap(),
        policyVersion = 2,
        resourceExactHosts = emptySet(),
        externalExactHosts = emptySet(),
        authExactHosts = emptySet(),
        requestRules = listOf(
            NetworkRequestRule(
                exactHosts = setOf("news.example"),
                operation = NetworkOperationPolicy(
                    name = NetworkOperations.SOURCE_READ,
                    methods = setOf("GET"),
                    pathPrefixes = setOf("/threads/"),
                    credentialed = true,
                ),
            ),
        ),
    )

    @Test fun `accepts exact authorized read operation`() {
        val url = validateSourceNetworkRequest(
            SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://news.example/threads/123"),
            policy,
        )
        assertEquals("news.example", url.host)
    }

    @Test fun `rejects same-host mutation confused deputy`() {
        assertRejected(SourceNetworkRequest(NetworkOperations.SOURCE_READ, "DELETE", "https://news.example/threads/123"))
    }

    @Test fun `rejects private or untrusted destinations and credential headers`() {
        assertRejected(SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://127.0.0.1/threads/123"))
        assertRejected(SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://metadata.google.internal/threads/123"))
        assertRejected(
            SourceNetworkRequest(
                NetworkOperations.SOURCE_READ,
                "GET",
                "https://news.example/threads/123",
                headers = mapOf("Cookie" to "session=stolen"),
            ),
        )
    }

    @Test fun `rejects same host route outside operation policy`() {
        assertRejected(SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://news.example/delete-account"))
    }

    @Test fun `host policy failure exposes only bounded host evidence`() {
        val error = runCatching {
            validateSourceNetworkRequest(
                SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://evil.example/private?token=secret"),
                policy,
            )
        }.exceptionOrNull() as SourceFailureException

        assertEquals(SourceFailureCode.HOST_POLICY, error.failure.code)
        assertEquals(NetworkOperations.SOURCE_READ, error.failure.operation)
        assertEquals("evil.example", error.failure.observedHost)
        assertEquals(listOf("news.example"), error.failure.allowedHosts)
        assertFalse(error.toString().contains("private"))
        assertFalse(error.toString().contains("secret"))
    }

    @Test fun `follows each supported same-origin redirect status`() {
        listOf(301, 302, 307, 308).forEach { redirectStatus ->
            val request = sourceReadRequest("https://news.example/threads/start")
            val initial = authorizeSourceNetworkRequest(request, policy)
            val requestedUrls = mutableListOf<String>()

            val response = followAuthorizedSourceRedirects(request, initial, policy) { url, credentialed ->
                assertTrue(credentialed)
                requestedUrls += url.toString()
                if (requestedUrls.size == 1) {
                    networkResponse(redirectStatus, "/threads/final")
                } else {
                    networkResponse(200)
                }
            }

            assertEquals(200, response.code)
            assertEquals(
                listOf(
                    "https://news.example/threads/start",
                    "https://news.example/threads/final",
                ),
                requestedUrls,
            )
        }
    }

    @Test fun `resolves protocol-relative redirect on the same origin`() {
        val request = sourceReadRequest("https://news.example/threads/start")
        val initial = authorizeSourceNetworkRequest(request, policy)
        val requestedUrls = mutableListOf<String>()

        val response = followAuthorizedSourceRedirects(request, initial, policy) { url, _ ->
            requestedUrls += url.toString()
            if (requestedUrls.size == 1) {
                networkResponse(302, "//news.example/threads/final?generation=2")
            } else {
                networkResponse(200)
            }
        }

        assertEquals(200, response.code)
        assertEquals(
            "https://news.example/threads/final?generation=2",
            requestedUrls.last(),
        )
    }

    @Test fun `rejects cross-origin redirect without issuing a second request`() {
        val request = sourceReadRequest("https://news.example/threads/start")
        val initial = authorizeSourceNetworkRequest(request, policy)
        var requestCount = 0

        val failure = captureSourceFailure {
            followAuthorizedSourceRedirects(request, initial, policy) { _, _ ->
                requestCount += 1
                networkResponse(302, "https://other.example/threads/final")
            }
        }

        assertEquals(SourceFailureCode.HOST_POLICY, failure.failure.code)
        assertEquals("other.example", failure.failure.observedHost)
        assertEquals(1, requestCount)
    }

    @Test fun `rejects redirect outside the authorized path`() {
        val request = sourceReadRequest("https://news.example/threads/start")
        val initial = authorizeSourceNetworkRequest(request, policy)
        var requestCount = 0

        val failure = captureSourceFailure {
            followAuthorizedSourceRedirects(request, initial, policy) { _, _ ->
                requestCount += 1
                networkResponse(301, "/delete-account")
            }
        }

        assertEquals(SourceFailureCode.HOST_POLICY, failure.failure.code)
        assertEquals(1, requestCount)
    }

    @Test fun `stops a redirect loop without leaking the URL`() {
        val request = sourceReadRequest("https://news.example/threads/start")
        val initial = authorizeSourceNetworkRequest(request, policy)
        var requestCount = 0

        val failure = captureSourceFailure {
            followAuthorizedSourceRedirects(request, initial, policy) { _, _ ->
                requestCount += 1
                when (requestCount) {
                    1 -> networkResponse(302, "/threads/next")
                    else -> networkResponse(302, "/threads/start")
                }
            }
        }

        assertEquals(SourceFailureCode.SITE_UNAVAILABLE, failure.failure.code)
        assertEquals(2, requestCount)
        assertFalse(failure.toString().contains("threads"))
    }

    @Test fun `missing or malformed redirect location is site unavailable`() {
        listOf<String?>(null, "https://[invalid").forEach { location ->
            val request = sourceReadRequest("https://news.example/threads/start")
            val initial = authorizeSourceNetworkRequest(request, policy)
            var requestCount = 0

            val failure = captureSourceFailure {
                followAuthorizedSourceRedirects(request, initial, policy) { _, _ ->
                    requestCount += 1
                    networkResponse(302, location)
                }
            }

            assertEquals(SourceFailureCode.SITE_UNAVAILABLE, failure.failure.code)
            assertEquals(1, requestCount)
        }
    }

    @Test fun `stops before issuing a sixth redirected request`() {
        val request = sourceReadRequest("https://news.example/threads/start")
        val initial = authorizeSourceNetworkRequest(request, policy)
        var requestCount = 0

        val failure = captureSourceFailure {
            followAuthorizedSourceRedirects(request, initial, policy) { _, _ ->
                requestCount += 1
                networkResponse(302, "/threads/hop-$requestCount")
            }
        }

        assertEquals(SourceFailureCode.SITE_UNAVAILABLE, failure.failure.code)
        assertEquals(MAX_SOURCE_REDIRECTS + 1, requestCount)
    }

    @Test fun `redirect cannot cross into a credentialed request rule`() {
        val publicRule = NetworkRequestRule(
            exactHosts = setOf("news.example"),
            operation = NetworkOperationPolicy(
                name = NetworkOperations.SOURCE_READ,
                methods = setOf("GET"),
                pathPrefixes = setOf("/public/"),
                credentialed = false,
            ),
        )
        val privateRule = NetworkRequestRule(
            exactHosts = setOf("news.example"),
            operation = NetworkOperationPolicy(
                name = NetworkOperations.SOURCE_READ,
                methods = setOf("GET"),
                pathPrefixes = setOf("/private/"),
                credentialed = true,
            ),
        )
        val scopedPolicy = SourceNetworkPolicy(
            exactHosts = setOf("news.example"),
            operations = emptyMap(),
            policyVersion = 2,
            requestRules = listOf(publicRule, privateRule),
        )
        val request = sourceReadRequest("https://news.example/public/start")
        val initial = authorizeSourceNetworkRequest(request, scopedPolicy)
        var requestCount = 0

        val failure = captureSourceFailure {
            followAuthorizedSourceRedirects(request, initial, scopedPolicy) { _, credentialed ->
                assertFalse(credentialed)
                requestCount += 1
                networkResponse(302, "/private/account")
            }
        }

        assertEquals(SourceFailureCode.HOST_POLICY, failure.failure.code)
        assertEquals(1, requestCount)
    }

    @Test fun `PTT capability reveals only the fixed consent predicate`() {
        val jar = RecordingCookieJar(
            listOf(
                Cookie.Builder().name("over18").value("1")
                    .hostOnlyDomain("www.ptt.cc").path("/").secure().build(),
                Cookie.Builder().name("session").value("secret")
                    .hostOnlyDomain("www.ptt.cc").path("/").secure().build(),
            ),
        )
        val result = executeNamedCookieOperation(
            identity = SourceIdentity(
                "tw.kevinzhang.newshub.extension.ptt",
                "trusted-signer",
                "tw.kevinzhang.newshub.extension.ptt",
            ),
            policy = SourceNetworkPolicy(
                exactHosts = setOf("www.ptt.cc"),
                operations = emptyMap(),
                namedCapabilities = setOf(NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS),
            ),
            cookieJar = jar,
            operation = ExtensionProtocol.COOKIE_OP_PTT_ADULT_CONSENT_STATUS,
            payload = "{}",
        )
        assertTrue(ExtensionWireJson.decode<Boolean>(result))
        assertTrue(jar.saved.isEmpty())
    }

    @Test fun `PTT capability cannot be used by a different Source`() {
        assertNamedRejected {
            executeNamedCookieOperation(
                identity = SourceIdentity("attacker.package", "trusted-signer", "attacker.source"),
                policy = SourceNetworkPolicy(
                    exactHosts = setOf("www.ptt.cc"),
                    operations = emptyMap(),
                    namedCapabilities = setOf(NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS),
                ),
                cookieJar = RecordingCookieJar(),
                operation = ExtensionProtocol.COOKIE_OP_PTT_ADULT_CONSENT_STATUS,
                payload = "{}",
            )
        }
    }

    @Test fun `PTT capability rejects parent-domain consent lookalike`() {
        val jar = RecordingCookieJar(
            listOf(
                Cookie.Builder().name("over18").value("1")
                    .domain("ptt.cc").path("/").secure().build(),
            ),
        )
        val result = executeNamedCookieOperation(
            identity = SourceIdentity(PTT_PACKAGE_FOR_TEST, "trusted-signer", PTT_SOURCE_FOR_TEST),
            policy = SourceNetworkPolicy(
                exactHosts = setOf("www.ptt.cc"),
                operations = emptyMap(),
                namedCapabilities = setOf(NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS),
            ),
            cookieJar = jar,
            operation = ExtensionProtocol.COOKIE_OP_PTT_ADULT_CONSENT_STATUS,
            payload = "{}",
        )
        assertFalse(ExtensionWireJson.decode<Boolean>(result))
    }

    @Test fun `external link validation is exact-host HTTPS only`() {
        val linkPolicy = SourceNetworkPolicy(
            exactHosts = setOf("news.example"),
            operations = emptyMap(),
            namedCapabilities = setOf(NamedHostCapabilities.EXTERNAL_LINK),
        )
        assertEquals("news.example", validateExternalLink("https://news.example/thread/1", linkPolicy).host)
        listOf(
            "http://news.example/thread/1",
            "https://evil.news.example/thread/1",
            "https://news.example:444/thread/1",
            "https://user:pass@news.example/thread/1",
            "https://127.0.0.1/thread/1",
            "https://[2001:db8::1]/thread/1",
        ).forEach { candidate ->
            assertTrue(runCatching { validateExternalLink(candidate, linkPolicy) }.isFailure)
        }
    }

    @Test fun `version two host scopes cannot borrow each others authority`() {
        val scoped = SourceNetworkPolicy(
            exactHosts = setOf("api.example.test"),
            operations = emptyMap(),
            namedCapabilities = setOf(
                NamedHostCapabilities.RESOURCE_READ,
                NamedHostCapabilities.EXTERNAL_LINK,
            ),
            policyVersion = 2,
            resourceExactHosts = setOf("images.example.test"),
            externalExactHosts = setOf("www.example.test"),
            authExactHosts = setOf("login.example.test"),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("api.example.test"),
                    NetworkOperationPolicy(NetworkOperations.SOURCE_READ, setOf("GET"), setOf("/v1/")),
                ),
            ),
        )

        assertEquals("images.example.test", validateResourceUrl("https://images.example.test/a.png", scoped).host)
        assertEquals("www.example.test", validateExternalLink("https://www.example.test/post/1", scoped).host)
        assertEquals("login.example.test", validateAuthUrl("https://login.example.test/session", scoped).host)
        listOf("images.example.test", "www.example.test", "login.example.test").forEach { host ->
            assertTrue(runCatching {
                validateSourceNetworkRequest(
                    SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", "https://$host/v1/items"),
                    scoped,
                )
            }.isFailure)
        }
        assertTrue(runCatching { validateResourceUrl("https://www.example.test/a.png", scoped) }.isFailure)
        assertTrue(runCatching { validateResourceUrl("https://login.example.test/a.png", scoped) }.isFailure)
        assertTrue(runCatching { validateExternalLink("https://images.example.test/post/1", scoped) }.isFailure)
        assertTrue(runCatching { validateAuthUrl("https://api.example.test/session", scoped) }.isFailure)
        assertTrue(runCatching { validateAuthUrl("https://www.example.test/session", scoped) }.isFailure)
    }

    @Test fun `Gamer board API is public while forum rule is credentialed`() {
        val gamer = SourceNetworkPolicy(
            exactHosts = setOf("api.gamer.com.tw", "forum.gamer.com.tw"),
            operations = emptyMap(),
            policyVersion = 2,
            resourceExactHosts = emptySet(),
            externalExactHosts = emptySet(),
            authExactHosts = setOf("forum.gamer.com.tw"),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("api.gamer.com.tw"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET"),
                        setOf("/community/v1/", "/mobile_app/forum/v3/"),
                        credentialed = false,
                    ),
                ),
                NetworkRequestRule(
                    setOf("forum.gamer.com.tw"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET"),
                        setOf("/B.php", "/C.php", "/ajax/"),
                        credentialed = true,
                    ),
                ),
            ),
        )
        val api = authorizeSourceNetworkRequest(
            SourceNetworkRequest(
                NetworkOperations.SOURCE_READ,
                "GET",
                "https://api.gamer.com.tw/mobile_app/forum/v3/bboards.php",
            ),
            gamer,
        )
        val forum = authorizeSourceNetworkRequest(
            SourceNetworkRequest(
                NetworkOperations.SOURCE_READ,
                "GET",
                "https://forum.gamer.com.tw/B.php?bsn=1",
            ),
            gamer,
        )
        assertFalse(api.rule.operation.credentialed)
        assertTrue(forum.rule.operation.credentialed)
    }

    @Test fun `overlapping request rules fail closed instead of choosing credentials`() {
        val overlapping = policy.copy(
            requestRules = policy.requestRules + policy.requestRules.single().copy(
                operation = policy.requestRules.single().operation.copy(credentialed = false),
            ),
        )
        assertTrue(
            runCatching {
                authorizeSourceNetworkRequest(
                    SourceNetworkRequest(
                        NetworkOperations.SOURCE_READ,
                        "GET",
                        "https://news.example/threads/123",
                    ),
                    overlapping,
                )
            }.isFailure,
        )
    }

    @Test fun `EYNY capability blindly writes only fixed proof cookies`() {
        val jar = RecordingCookieJar()
        val now = 1_800_000_000_000L
        val proof = EynyChallengeProof(
            host = "www.eyny.com",
            cookiePrefix = "9bd3f9c",
            nonce = 119_310,
            timestamp = "1784585056",
            challenge = "0979c1c29ad14faae09cf23af6e79666",
        )
        val result = executeNamedCookieOperation(
            identity = SourceIdentity(
                "tw.kevinzhang.newshub.extension.eyny",
                "trusted-signer",
                "tw.kevinzhang.eyny",
            ),
            policy = SourceNetworkPolicy(
                exactHosts = setOf("eyny.com", "www.eyny.com"),
                operations = emptyMap(),
                namedCapabilities = setOf(NamedHostCapabilities.EYNY_CHALLENGE_PROOF),
            ),
            cookieJar = jar,
            operation = ExtensionProtocol.COOKIE_OP_EYNY_CHALLENGE_PROOF,
            payload = ExtensionWireJson.encode(proof),
            now = now,
        )
        assertTrue(ExtensionWireJson.decode<Boolean>(result))
        assertEquals(6, jar.saved.size)
        assertEquals(
            setOf("9bd3f9c_n", "9bd3f9c_ts", "9bd3f9c_ch"),
            jar.saved.mapTo(linkedSetOf()) { it.name },
        )
        assertEquals(setOf("www.eyny.com", "eyny.com"), jar.saved.mapTo(linkedSetOf()) { it.domain })
        assertTrue(jar.saved.all { it.path == "/" && it.secure })
        assertTrue(jar.saved.all { it.expiresAt == now + 86_400_000L })
        assertFalse(jar.saved.any { it.name == "session" || it.name == "auth" })
    }

    @Test fun `EYNY apex proof avoids duplicate RFC cookie identities`() {
        val jar = RecordingCookieJar()
        executeNamedCookieOperation(
            identity = SourceIdentity(
                "tw.kevinzhang.newshub.extension.eyny",
                "trusted-signer",
                "tw.kevinzhang.eyny",
            ),
            policy = SourceNetworkPolicy(
                exactHosts = setOf("eyny.com", "www.eyny.com"),
                operations = emptyMap(),
                namedCapabilities = setOf(NamedHostCapabilities.EYNY_CHALLENGE_PROOF),
            ),
            cookieJar = jar,
            operation = ExtensionProtocol.COOKIE_OP_EYNY_CHALLENGE_PROOF,
            payload = ExtensionWireJson.encode(
                EynyChallengeProof(
                    host = "eyny.com",
                    cookiePrefix = "9bd3f9c",
                    nonce = 4,
                    timestamp = "1784585056",
                    challenge = "0979c1c29ad14faae09cf23af6e79666",
                ),
            ),
        )
        assertEquals(3, jar.saved.size)
        assertTrue(jar.saved.all { it.domain == "eyny.com" && !it.hostOnly })
    }

    private fun assertRejected(request: SourceNetworkRequest) {
        try {
            validateSourceNetworkRequest(request, policy)
            fail("Expected request to be rejected")
        } catch (_: SourceFailureException) {
            // expected
        }
    }

    private fun sourceReadRequest(url: String) = SourceNetworkRequest(
        operation = NetworkOperations.SOURCE_READ,
        method = "GET",
        url = url,
    )

    private fun networkResponse(code: Int, location: String? = null) = SourceNetworkResponse(
        code = code,
        headers = location?.let { mapOf("Location" to it) }.orEmpty(),
        body = ByteArray(0),
    )

    private fun captureSourceFailure(block: () -> Unit): SourceFailureException {
        val error = runCatching(block).exceptionOrNull()
        if (error !is SourceFailureException) {
            fail("Expected SourceFailureException, got ${error?.javaClass?.simpleName}")
        }
        return error as SourceFailureException
    }

    private fun assertNamedRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected named capability to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    private class RecordingCookieJar(initial: List<Cookie> = emptyList()) : CookieJar {
        private val existing = initial.toMutableList()
        val saved = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            saved += cookies
            existing += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = existing.filter { it.matches(url) }
    }

    private companion object {
        const val PTT_PACKAGE_FOR_TEST = "tw.kevinzhang.newshub.extension.ptt"
        const val PTT_SOURCE_FOR_TEST = "tw.kevinzhang.newshub.extension.ptt"
    }
}
