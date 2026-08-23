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
                "\"namedCapabilities\":[\"external_link\",\"resource_read\"]," +
                "\"operations\":[{\"credentialed\":true,\"methods\":[\"GET\",\"HEAD\"]," +
                "\"name\":\"source_read\",\"pathPrefixes\":[\"/\"]}]}",
            policy.canonicalJson(),
        )
        assertEquals("5ccfec4d87931829e1c45047e39386c4cb2a8dc959ac4a6d90ad49462767db6a", policy.sha256())
    }

    @Test fun `Hacker News policy hash matches signed repository target`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("news.ycombinator.com"),
            operations = mapOf(
                NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                    name = NetworkOperations.SOURCE_READ,
                    methods = setOf("GET", "HEAD"),
                    pathPrefixes = setOf("/"),
                    credentialed = true,
                ),
            ),
            namedCapabilities = setOf(NamedHostCapabilities.RESOURCE_READ, NamedHostCapabilities.EXTERNAL_LINK),
        )

        assertEquals(
            "{\"exactHosts\":[\"news.ycombinator.com\"]," +
                "\"namedCapabilities\":[\"external_link\",\"resource_read\"]," +
                "\"operations\":[{\"credentialed\":true,\"methods\":[\"GET\",\"HEAD\"]," +
                "\"name\":\"source_read\",\"pathPrefixes\":[\"/\"]}]}",
            policy.canonicalJson(),
        )
        assertEquals("7916aa87fa766710f2cd0b56e41bfa36a7f2c61ef0f92891e2956aff64ef3fa5", policy.sha256())
    }

    @Test fun `version two policy hashes four independent exact host scopes`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("api.example.com"),
            operations = emptyMap(),
            requestRules = listOf(
                NetworkRequestRule(
                    exactHosts = setOf("api.example.com"),
                    operation = NetworkOperationPolicy(
                        name = NetworkOperations.SOURCE_READ,
                        methods = setOf("GET"),
                        pathPrefixes = setOf("/v1/"),
                        credentialed = false,
                    ),
                ),
            ),
            namedCapabilities = setOf(
                NamedHostCapabilities.EXTERNAL_LINK,
                NamedHostCapabilities.RESOURCE_READ,
            ),
            policyVersion = 2,
            resourceExactHosts = setOf("images.example.com"),
            externalExactHosts = setOf("www.example.com"),
            authExactHosts = setOf("login.example.com"),
        )

        assertEquals(
            "{\"auth\":{\"exactHosts\":[\"login.example.com\"]}," +
                "\"external\":{\"exactHosts\":[\"www.example.com\"]}," +
                "\"namedCapabilities\":[\"external_link\",\"resource_read\"]," +
                "\"request\":{\"rules\":[{\"exactHosts\":[\"api.example.com\"]," +
                "\"operation\":{\"credentialed\":false,\"methods\":[\"GET\"]," +
                "\"name\":\"source_read\",\"pathPrefixes\":[\"/v1/\"]}}]}," +
                "\"resource\":{\"exactHosts\":[\"images.example.com\"]},\"schemaVersion\":2}",
            policy.canonicalJson(),
        )
        assertEquals(
            setOf("api.example.com", "images.example.com", "www.example.com", "login.example.com"),
            policy.allExactHosts,
        )
    }

    @Test fun `Gamer reviewed request rules match producer policy hash`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("api.gamer.com.tw", "forum.gamer.com.tw"),
            operations = emptyMap(),
            namedCapabilities = setOf(
                NamedHostCapabilities.EXTERNAL_LINK,
                NamedHostCapabilities.RESOURCE_READ,
            ),
            policyVersion = 2,
            resourceExactHosts = setOf("i2.bahamut.com.tw"),
            externalExactHosts = setOf("forum.gamer.com.tw"),
            authExactHosts = setOf("forum.gamer.com.tw", "user.gamer.com.tw", "www.gamer.com.tw"),
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

        assertEquals(
            "d83562d39c756463f9e5d1ed8028cfde5ba53abb821d5f3c2e45ec71bcefc5dc",
            policy.sha256(),
        )
    }

    @Test fun `version two policy rejects cross-scope widening primitives`() {
        val base = SourceNetworkPolicy(
            exactHosts = setOf("api.example.com"),
            operations = emptyMap(),
            requestRules = listOf(
                NetworkRequestRule(
                    exactHosts = setOf("api.example.com"),
                    operation = NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET"),
                        setOf("/"),
                    ),
                ),
            ),
            policyVersion = 2,
            resourceExactHosts = emptySet(),
            externalExactHosts = emptySet(),
            authExactHosts = emptySet(),
        )
        base.canonicalJson()
        assertInvalid {
            base.copy(
                operations = mapOf(NetworkOperations.SOURCE_READ to base.requestRules.single().operation),
            ).canonicalJson()
        }
        assertInvalid { base.copy(exactHosts = setOf("*.example.com")).canonicalJson() }
        assertInvalid {
            base.copy(
                requestRules = listOf(
                    NetworkRequestRule(
                        exactHosts = setOf("api.example.com"),
                        operation = NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("POST"),
                        setOf("/"),
                    ),
                    ),
                ),
            ).canonicalJson()
        }
        assertInvalid { base.copy(namedCapabilities = setOf("raw_socket")).canonicalJson() }
        assertInvalid { base.copy(resourceExactHosts = setOf("http://images.example.com")).canonicalJson() }
    }

    @Test fun `version three signs exact credentialed resource user agent`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("forum.example.com"),
            operations = emptyMap(),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("forum.example.com"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET", "HEAD"),
                        setOf("/"),
                        credentialed = true,
                    ),
                ),
            ),
            namedCapabilities = setOf(NamedHostCapabilities.RESOURCE_READ),
            policyVersion = 3,
            resourceExactHosts = setOf("cdn.example.com", "forum.example.com"),
            externalExactHosts = emptySet(),
            authExactHosts = setOf("forum.example.com"),
            resourceRules = listOf(
                ResourceNetworkRule(
                    setOf("forum.example.com"),
                    true,
                    "NewsHub Browser/1.0",
                    setOf("/attachments/"),
                ),
                ResourceNetworkRule(setOf("cdn.example.com"), pathPrefixes = setOf("/images/")),
            ),
        )

        assertEquals(
            "{\"auth\":{\"exactHosts\":[\"forum.example.com\"]}," +
                "\"external\":{\"exactHosts\":[]},\"namedCapabilities\":[\"resource_read\"]," +
                "\"request\":{\"rules\":[{\"exactHosts\":[\"forum.example.com\"]," +
                "\"operation\":{\"credentialed\":true,\"methods\":[\"GET\",\"HEAD\"]," +
                "\"name\":\"source_read\",\"pathPrefixes\":[\"/\"]}}]}," +
                "\"resource\":{\"rules\":[{\"credentialed\":false," +
                "\"exactHosts\":[\"cdn.example.com\"],\"exactPaths\":[],\"pathPrefixes\":[\"/images/\"],\"userAgent\":null},{\"credentialed\":true," +
                "\"exactHosts\":[\"forum.example.com\"],\"exactPaths\":[],\"pathPrefixes\":[\"/attachments/\"]," +
                "\"userAgent\":\"NewsHub Browser/1.0\"}]}," +
                "\"schemaVersion\":3}",
            policy.canonicalJson(),
        )

        assertInvalid { policy.copy(resourceRules = emptyList()).canonicalJson() }
        assertInvalid {
            policy.copy(
                resourceRules = listOf(ResourceNetworkRule(setOf("forum.example.com"), true, null)),
            ).canonicalJson()
        }
        assertInvalid {
            policy.copy(
                resourceRules = policy.resourceRules +
                    ResourceNetworkRule(setOf("forum.example.com"), false, null),
            ).canonicalJson()
        }
        assertInvalid { policy.copy(authExactHosts = emptySet()).canonicalJson() }
    }

    @Test fun `version three policy hash matches repository publisher corpus`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("forum.example.com"),
            operations = emptyMap(),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("forum.example.com"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET", "HEAD"),
                        setOf("/"),
                        credentialed = true,
                    ),
                ),
            ),
            namedCapabilities = setOf(
                NamedHostCapabilities.RESOURCE_READ,
                NamedHostCapabilities.EXTERNAL_LINK,
            ),
            policyVersion = 3,
            resourceExactHosts = setOf("forum.example.com", "cdn.example.com"),
            externalExactHosts = setOf("forum.example.com"),
            authExactHosts = setOf("forum.example.com"),
            resourceRules = listOf(
                ResourceNetworkRule(
                    setOf("forum.example.com"),
                    credentialed = true,
                    userAgent = "NewsHub Extension Browser/1.0",
                    pathPrefixes = setOf("/attachments/"),
                ),
                ResourceNetworkRule(setOf("cdn.example.com"), pathPrefixes = setOf("/images/")),
            ),
        )

        assertEquals(
            "3c6c6b1b85031c873f962b0b53716725972b569c02a1c5d99f70d55035ef9c7a",
            policy.sha256(),
        )
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

        assertEquals("www52.eyny.com", proof.copy(host = "www52.eyny.com").host)
        assertEquals("www53.eyny.com", proof.copy(host = "www53.eyny.com").host)

        assertInvalid { proof.copy(host = "attacker.example") }
        assertInvalid { proof.copy(host = "www54.eyny.com") }
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
