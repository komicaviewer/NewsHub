package tw.kevinzhang.marketplace

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256
import java.io.File

class TrustedRepositoryClientTest {
    private val embeddedRoot = productionRootBytes()
    private val rootInspection = inspectTrustedRoot(embeddedRoot)
    private val domain = RepositoryTrustDomain(
        id = "44444444-4444-4444-8444-444444444444",
        canonicalBaseUrl = "https://repo.example.test/distribution",
        trustMode = RepositoryTrustMode.USER_PINNED,
        state = RepositoryDomainState.ACTIVE,
        rootThreshold = rootInspection.threshold,
        rootKeyFingerprints = rootInspection.keyFingerprints,
    )
    private val client = TrustedRepositoryClient(
        baseClient = OkHttpClient(),
        embeddedRoot = embeddedRoot,
        stateStore = EmptyStateStore,
        domain = domain,
    )

    @Test
    fun `valid third party full policy is canonicalized and domain scoped`() {
        val policy = validPolicy()
        val result = client.parseTargets(domain.baseUrl, targets(policy), 3, 7, 99_000)

        assertEquals(domain.id, result.trust.repositoryDomainId)
        assertEquals(domain.id, result.trust.policies.single().repositoryDomainId)
        assertEquals(domain.id, result.extensions.single().repositoryDomainId)
        val source = result.trust.policies.single().sources.getValue("source.test")
        assertEquals(policy, source.networkPolicy)
        assertEquals(policy.sha256(), source.policyHash)
    }

    @Test
    fun `wildcard POST unknown capability and hash mismatch fail closed`() {
        val wildcard = policyObject(validPolicy()).also {
            it.add("exactHosts", JsonArray().apply { add("*.example.test") })
        }
        assertPolicyRejected(wildcard, validPolicy().sha256())

        val post = policyObject(validPolicy()).also {
            it.getAsJsonArray("operations")[0].asJsonObject.add(
                "methods", JsonArray().apply { add("POST") },
            )
        }
        assertPolicyRejected(post, validPolicy().sha256())

        val unknownCapability = policyObject(validPolicy()).also {
            it.add("namedCapabilities", JsonArray().apply { add("raw_socket") })
        }
        assertPolicyRejected(unknownCapability, validPolicy().sha256())

        val unknownOperation = policyObject(validPolicy()).also {
            it.getAsJsonArray("operations")[0].asJsonObject.addProperty("name", "raw_socket")
        }
        assertPolicyRejected(unknownOperation, validPolicy().sha256())

        assertPolicyRejected(policyObject(validPolicy()), "0".repeat(64))
    }

    @Test
    fun `unknown network policy field fails closed`() {
        val policyObject = policyObject(validPolicy()).apply { addProperty("allowCookies", true) }
        val error = assertThrows(TrustedMetadataException::class.java) {
            client.parseTargets(
                domain.baseUrl,
                targets(policyObject, validPolicy().sha256()),
                1,
                1,
                99_000,
            )
        }
        assertTrue(error.message.orEmpty().contains("unknown or missing"))
    }

    @Test
    fun `domain root pin mismatch and inactive state fail closed`() {
        assertThrows(TrustedMetadataException::class.java) {
            TrustedRepositoryClient(
                OkHttpClient(),
                embeddedRoot,
                EmptyStateStore,
                domain.copy(
                    rootKeyFingerprints = domain.rootKeyFingerprints.drop(1).toSet() + "f".repeat(64),
                ),
            )
        }
        val suspended = TrustedRepositoryClient(
            OkHttpClient(),
            embeddedRoot,
            EmptyStateStore,
            domain.copy(state = RepositoryDomainState.SUSPENDED),
        )
        assertThrows(TrustedMetadataException::class.java) { suspended.loadPersistedSnapshot() }
    }

    private fun assertPolicyRejected(policy: JsonObject, hash: String) {
        assertThrows(TrustedMetadataException::class.java) {
            client.parseTargets(domain.baseUrl, targets(policy, hash), 1, 1, 99_000)
        }
    }

    private fun validPolicy() = SourceNetworkPolicy(
        exactHosts = setOf("api.example.test"),
        operations = mapOf(
            NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                name = NetworkOperations.SOURCE_READ,
                methods = setOf("GET", "HEAD"),
                pathPrefixes = setOf("/"),
                credentialed = true,
            ),
        ),
        namedCapabilities = setOf(NamedHostCapabilities.RESOURCE_READ),
    )

    private fun targets(policy: SourceNetworkPolicy) = targets(policyObject(policy), policy.sha256())

    private fun targets(networkPolicy: JsonObject, policyHash: String): JsonObject = JsonObject().apply {
        add("custom", JsonObject().apply {
            add("repository", JsonObject().apply {
                addProperty("name", "Test repository")
                addProperty("description", "Test")
            })
        })
        add("targets", JsonObject().apply {
            add("apk/test.apk", JsonObject().apply {
                addProperty("length", 1)
                add("hashes", JsonObject().apply { addProperty("sha256", "b".repeat(64)) })
                add("custom", JsonObject().apply {
                    addProperty("packageName", "org.example.extension")
                    addProperty("name", "Example")
                    addProperty("versionCode", 1)
                    addProperty("versionName", "1.0")
                    addProperty("lang", "en")
                    add("apkSignerPins", JsonArray().apply { add("c".repeat(64)) })
                    addProperty("lineageRootSha256", "c".repeat(64))
                    add("sources", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("id", "source.test")
                            addProperty("name", "Test")
                            addProperty("lang", "en")
                            addProperty("baseUrl", "https://api.example.test")
                            addProperty("service", "org.example.ExtensionService")
                            addProperty("protocol", 1)
                            addProperty("policyHash", policyHash)
                            add("networkPolicy", networkPolicy)
                        })
                    })
                })
            })
        })
    }

    private fun policyObject(policy: SourceNetworkPolicy) = JsonObject().apply {
        add("exactHosts", JsonArray().apply { policy.exactHosts.sorted().forEach(::add) })
        add("operations", JsonArray().apply {
            policy.operations.values.sortedBy { it.name }.forEach { operation ->
                add(JsonObject().apply {
                    addProperty("name", operation.name)
                    add("methods", JsonArray().apply { operation.methods.sorted().forEach(::add) })
                    add("pathPrefixes", JsonArray().apply { operation.pathPrefixes.sorted().forEach(::add) })
                    addProperty("credentialed", operation.credentialed)
                })
            }
        })
        add("namedCapabilities", JsonArray().apply { policy.namedCapabilities.sorted().forEach(::add) })
    }

    private object EmptyStateStore : RepositoryStateStore {
        override fun loadVersions() = RepositoryVersions()
        override fun loadRoot(): ByteArray? = null
        override fun loadTargets(): ByteArray? = null
        override fun save(
            root: ByteArray,
            timestamp: ByteArray,
            snapshot: ByteArray,
            targets: ByteArray,
            versions: RepositoryVersions,
        ) = Unit
    }

    private fun productionRootBytes(): ByteArray = listOf(
        File("marketplace/src/main/assets/extension-root.json"),
        File("src/main/assets/extension-root.json"),
    ).firstOrNull(File::isFile)?.readBytes() ?: error("Production TUF root fixture is missing")
}
