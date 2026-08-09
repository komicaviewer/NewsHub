package tw.kevinzhang.newshub.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceIdentity
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
        operations = mapOf(
            "thread" to NetworkOperationPolicy(
                name = "thread",
                methods = setOf("GET"),
                pathPrefixes = setOf("/threads/"),
                credentialed = true,
            ),
        ),
    )

    @Test fun `accepts exact authorized read operation`() {
        val url = validateSourceNetworkRequest(
            SourceNetworkRequest("thread", "GET", "https://news.example/threads/123"),
            policy,
        )
        assertEquals("news.example", url.host)
    }

    @Test fun `rejects same-host mutation confused deputy`() {
        assertRejected(SourceNetworkRequest("thread", "DELETE", "https://news.example/threads/123"))
    }

    @Test fun `rejects private or untrusted destinations and credential headers`() {
        assertRejected(SourceNetworkRequest("thread", "GET", "https://127.0.0.1/threads/123"))
        assertRejected(SourceNetworkRequest("thread", "GET", "https://metadata.google.internal/threads/123"))
        assertRejected(
            SourceNetworkRequest(
                "thread",
                "GET",
                "https://news.example/threads/123",
                headers = mapOf("Cookie" to "session=stolen"),
            ),
        )
    }

    @Test fun `rejects same host route outside operation policy`() {
        assertRejected(SourceNetworkRequest("thread", "GET", "https://news.example/delete-account"))
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
        ).forEach { candidate ->
            assertTrue(runCatching { validateExternalLink(candidate, linkPolicy) }.isFailure)
        }
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
        } catch (_: IllegalArgumentException) {
            // expected
        }
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
