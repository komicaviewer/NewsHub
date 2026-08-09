package tw.kevinzhang.newshub.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceNetworkRequest

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

    private fun assertRejected(request: SourceNetworkRequest) {
        try {
            validateSourceNetworkRequest(request, policy)
            fail("Expected request to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
