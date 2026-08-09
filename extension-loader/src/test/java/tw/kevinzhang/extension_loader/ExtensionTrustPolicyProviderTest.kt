package tw.kevinzhang.extension_loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionTrustPolicyProviderTest {
    private val signer = "a".repeat(64)
    private val policyHash = "b".repeat(64)

    @Test fun `empty and expired trust state fails closed`() {
        val provider = ExtensionTrustPolicyProvider()
        assertNull(provider.policyFor("example.extension", "example.source"))
        assertTrue(
            runCatching {
                provider.installVerifiedSnapshot(snapshot(expiresAt = System.currentTimeMillis() - 1))
            }.isFailure,
        )
    }

    @Test fun `verified package policy is exact and rollback protected`() {
        val provider = ExtensionTrustPolicyProvider()
        provider.installVerifiedSnapshot(snapshot(rootVersion = 2, targetsVersion = 4))
        assertEquals(
            policyHash,
            provider.policyFor("example.extension", "example.source")
                ?.sources
                ?.get("example.source")
                ?.policyHash,
        )
        assertNull(provider.policyFor("attacker.extension", "example.source"))
        assertNull(provider.policyFor("example.extension", "attacker.source"))
        assertTrue(runCatching { provider.installVerifiedSnapshot(snapshot(rootVersion = 1, targetsVersion = 5)) }.isFailure)
        assertTrue(runCatching { provider.installVerifiedSnapshot(snapshot(rootVersion = 3, targetsVersion = 3)) }.isFailure)
    }

    @Test fun `same metadata versions cannot replace policy content`() {
        val provider = ExtensionTrustPolicyProvider()
        val original = snapshot(rootVersion = 2, targetsVersion = 4)
        provider.installVerifiedSnapshot(original)
        provider.installVerifiedSnapshot(original)

        val replacement = original.copy(
            policies = original.policies.map { policy ->
                policy.copy(targetSha256 = "d".repeat(64))
            },
        )
        assertTrue(runCatching { provider.installVerifiedSnapshot(replacement) }.isFailure)
    }

    @Test fun `root rotation cannot replace unchanged targets policy`() {
        val provider = ExtensionTrustPolicyProvider()
        val original = snapshot(rootVersion = 2, targetsVersion = 4)
        provider.installVerifiedSnapshot(original)

        val rootRotation = original.copy(rootVersion = 3)
        provider.installVerifiedSnapshot(rootRotation)

        val changedPolicy = rootRotation.copy(
            rootVersion = 4,
            policies = rootRotation.policies.map { it.copy(targetSha256 = "e".repeat(64)) },
        )
        assertTrue(runCatching { provider.installVerifiedSnapshot(changedPolicy) }.isFailure)
        assertTrue(
            runCatching {
                provider.installVerifiedSnapshot(rootRotation.copy(rootVersion = 4, expiresAtEpochMillis = rootRotation.expiresAtEpochMillis + 1))
            }.isFailure,
        )
    }

    private fun snapshot(
        rootVersion: Long = 1,
        targetsVersion: Long = 1,
        expiresAt: Long = System.currentTimeMillis() + 60_000,
    ) = VerifiedExtensionTrustSnapshot(
        rootVersion = rootVersion,
        targetsVersion = targetsVersion,
        expiresAtEpochMillis = expiresAt,
        policies = listOf(
            ExtensionSigningPolicy(
                packageName = "example.extension",
                expectedVersionCode = 7,
                targetLength = 4,
                targetSha256 = "c".repeat(64),
                lineageAnchorsSha256 = setOf(signer),
                approvedCurrentSignersSha256 = setOf(signer),
                sources = mapOf(
                    "example.source" to ExpectedSourceService(
                        serviceClassName = "example.extension.ExampleService",
                        name = "Example",
                        lang = "en",
                        baseUrl = "https://example.com",
                        protocol = 1,
                        policyHash = policyHash,
                    ),
                ),
            ),
        ),
    )
}
