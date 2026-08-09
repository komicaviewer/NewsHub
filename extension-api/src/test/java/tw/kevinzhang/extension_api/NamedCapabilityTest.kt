package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NamedCapabilityTest {
    @Test fun `network policy hash uses stable sorted canonical JSON`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("www.example.com", "example.com"),
            operations = mapOf(
                NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                    name = NetworkOperations.SOURCE_READ,
                    methods = setOf("HEAD", "GET"),
                    pathPrefixes = setOf("/"),
                    credentialed = true,
                ),
            ),
            namedCapabilities = setOf(NamedHostCapabilities.RESOURCE_READ, NamedHostCapabilities.EXTERNAL_LINK),
        )
        assertEquals(
            "{\"exactHosts\":[\"example.com\",\"www.example.com\"]," +
                "\"operations\":[{\"name\":\"source_read\",\"methods\":[\"GET\",\"HEAD\"]," +
                "\"pathPrefixes\":[\"/\"],\"credentialed\":true}]," +
                "\"namedCapabilities\":[\"external_link\",\"resource_read\"]}",
            policy.canonicalJson(),
        )
        assertTrue(policy.sha256().matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `EYNY proof accepts only the fixed bounded schema`() {
        val proof = EynyChallengeProof(
            host = "www.eyny.com",
            cookiePrefix = "9bd3f9c",
            nonce = 119_310,
            timestamp = "1784585056",
            challenge = "0979c1c29ad14faae09cf23af6e79666",
        )
        assertEquals("9bd3f9c", proof.cookiePrefix)

        assertInvalid { proof.copy(host = "attacker.example") }
        assertInvalid { proof.copy(cookiePrefix = "session") }
        assertInvalid { proof.copy(cookiePrefix = "9bd3f9c; Domain=attacker.example") }
        assertInvalid { proof.copy(nonce = 2_000_001) }
        assertInvalid { proof.copy(timestamp = "now") }
        assertInvalid { proof.copy(challenge = "not-hex") }
    }

    @Test
    fun `external link handles reject naked and stale-looking models`() {
        val handle = ExternalLinkHandle(
            sourceSession = "0123456789abcdef",
            generation = 4,
            token = "abcdefghijklmnopqrstuvwxyzABCDEF",
        )
        assertEquals(handle, ExternalLinkHandle.parse(handle.asModel()))
        assertNull(ExternalLinkHandle.parse("https://example.com"))
        assertNull(
            ExternalLinkHandle.parse(
                "newshub-link://0123456789abcdef/0/abcdefghijklmnopqrstuvwxyzABCDEF",
            ),
        )
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid capability payload")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
