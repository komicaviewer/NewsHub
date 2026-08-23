package tw.kevinzhang.extension_loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.NetworkRequestRule
import tw.kevinzhang.extension_api.ResourceNetworkRule
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256

class ExtensionTrustPolicyProviderTest {
    private val signer = "a".repeat(64)
    private val domainA = "10000000-0000-0000-0000-000000000001"
    private val domainB = "20000000-0000-0000-0000-000000000002"

    @Test fun `empty and expired trust state fails closed`() {
        val provider = ExtensionTrustPolicyProvider()
        assertNull(provider.policyFor("example.extension", "example.source"))
        assertTrue(
            runCatching {
                provider.installVerifiedSnapshot(snapshot(expiresAt = System.currentTimeMillis() - 1))
            }.isFailure,
        )
    }

    @Test fun `verified package policy is exact and rollback protected per domain`() {
        val provider = ExtensionTrustPolicyProvider()
        provider.installVerifiedSnapshot(snapshot(domain = domainA, rootVersion = 2, targetsVersion = 4))
        provider.installVerifiedSnapshot(
            snapshot(
                domain = domainB,
                packageName = "second.extension",
                sourceId = "second.source",
                rootVersion = 1,
                targetsVersion = 1,
            ),
        )

        assertNotNull(provider.policyFor("example.extension", "example.source"))
        assertNotNull(provider.policyFor("second.extension", "second.source"))
        assertNull(provider.policyFor("attacker.extension", "example.source"))
        assertTrue(
            runCatching {
                provider.installVerifiedSnapshot(snapshot(domain = domainA, rootVersion = 1, targetsVersion = 5))
            }.isFailure,
        )
        provider.installVerifiedSnapshot(
            snapshot(
                domain = domainB,
                packageName = "second.extension",
                sourceId = "second.source",
                rootVersion = 2,
                targetsVersion = 2,
            ),
        )
        assertNotNull(provider.policyFor("second.extension", "second.source"))
    }

    @Test fun `root rotation cannot replace unchanged targets policy`() {
        val provider = ExtensionTrustPolicyProvider()
        val original = snapshot(domain = domainA, rootVersion = 2, targetsVersion = 4)
        provider.installVerifiedSnapshot(original)
        provider.installVerifiedSnapshot(original.copy(rootVersion = 3))

        val changedPolicy = original.copy(
            rootVersion = 4,
            policies = original.policies.map { it.copy(targetSha256 = "e".repeat(64)) },
        )
        assertTrue(runCatching { provider.installVerifiedSnapshot(changedPolicy) }.isFailure)
    }

    @Test fun `expiry suspension and revocation of one domain do not affect another`() {
        val provider = ExtensionTrustPolicyProvider()
        val expiresAt = System.currentTimeMillis() + 60_000
        provider.installVerifiedSnapshot(snapshot(domain = domainA, expiresAt = expiresAt))
        provider.installVerifiedSnapshot(
            snapshot(domain = domainB, packageName = "second.extension", sourceId = "second.source"),
        )

        assertNull(provider.policyForPackage("example.extension", now = expiresAt + 1))
        assertNotNull(provider.policyForPackage("second.extension"))
        assertEquals(RepositoryTrustDomainState.EXPIRED, provider.domainStates(expiresAt + 1)[domainA])

        provider.setDomainState(domainA, RepositoryTrustDomainState.SUSPENDED)
        assertNull(provider.policyForPackage("example.extension"))
        assertNotNull(provider.policyForPackage("second.extension"))
        provider.installVerifiedSnapshot(snapshot(domain = domainA, targetsVersion = 2))
        assertEquals(RepositoryTrustDomainState.SUSPENDED, provider.domainStates()[domainA])
        assertNull(provider.policyForPackage("example.extension"))
        provider.setDomainState(domainA, RepositoryTrustDomainState.REVOKED)
        assertTrue(
            runCatching { provider.setDomainState(domainA, RepositoryTrustDomainState.ACTIVE) }.isFailure,
        )
        assertNotNull(provider.policyForPackage("second.extension"))
    }

    @Test fun `cold restored inactive domain remains visible without authorizing its package`() {
        val provider = ExtensionTrustPolicyProvider()

        provider.setDomainState(domainA, RepositoryTrustDomainState.REVOKED)

        assertEquals(RepositoryTrustDomainState.REVOKED, provider.domainStates()[domainA])
        assertNull(provider.policyForPackage("example.extension"))
        assertTrue(
            runCatching { provider.setDomainState(domainA, RepositoryTrustDomainState.ACTIVE) }.isFailure,
        )
    }

    @Test fun `package conflict blocks every owner until package domain is explicitly selected`() {
        val provider = ExtensionTrustPolicyProvider()
        provider.installVerifiedSnapshot(snapshot(domain = domainA))
        provider.installVerifiedSnapshot(snapshot(domain = domainB))

        assertNull(provider.policyForPackage("example.extension"))
        provider.selectPackageDomain("example.extension", domainB)
        assertEquals(domainB, provider.policyForPackage("example.extension")?.repositoryDomainId)
        provider.setDomainState(domainB, RepositoryTrustDomainState.REVOKED)
        assertNull(provider.policyForPackage("example.extension"))
        provider.selectPackageDomain("example.extension", null)
        assertEquals(domainA, provider.policyForPackage("example.extension")?.repositoryDomainId)
    }

    @Test fun `source conflict across packages blocks both until source domain is explicitly selected`() {
        val provider = ExtensionTrustPolicyProvider()
        provider.installVerifiedSnapshot(snapshot(domain = domainA))
        provider.installVerifiedSnapshot(
            snapshot(domain = domainB, packageName = "second.extension", sourceId = "example.source"),
        )

        assertNull(provider.policyForPackage("example.extension"))
        assertNull(provider.policyForPackage("second.extension"))
        provider.selectSourceDomain("example.source", domainA)
        assertNotNull(provider.policyForPackage("example.extension"))
        assertNull(provider.policyForPackage("second.extension"))
    }

    @Test fun `signed full policy rejects wildcard POST unknown capability and hash mismatch`() {
        val valid = networkPolicy("example.com")
        expectedService(domainA, "example.source", valid)

        listOf(
            valid.copy(exactHosts = setOf("*.example.com")),
            valid.copy(
                operations = valid.operations.mapValues { (_, operation) ->
                    operation.copy(methods = setOf("POST"))
                },
            ),
            valid.copy(namedCapabilities = setOf("raw_socket")),
        ).forEach { invalid ->
            assertTrue(
                runCatching { expectedService(domainA, "example.source", invalid) }.isFailure,
            )
        }
        assertTrue(
            runCatching {
                expectedService(domainA, "example.source", valid, hash = "f".repeat(64))
            }.isFailure,
        )
    }

    @Test fun `version two keeps request authority separate while accepting base host in another scope`() {
        val policy = networkPolicy("api.example.com").copy(
            operations = emptyMap(),
            policyVersion = 2,
            resourceExactHosts = setOf("cdn.example.com"),
            externalExactHosts = setOf("example.com"),
            authExactHosts = setOf("login.example.com"),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("api.example.com"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET", "HEAD"),
                        setOf("/"),
                        credentialed = true,
                    ),
                ),
            ),
        )

        val service = expectedService(domainA, "example.source", policy)
        assertEquals(setOf("api.example.com"), service.networkPolicy?.exactHosts)
        assertEquals(
            setOf("api.example.com", "cdn.example.com", "example.com", "login.example.com"),
            service.networkPolicy?.allExactHosts,
        )
    }

    @Test fun `version three accepts path scoped credentialed resources`() {
        val policy = networkPolicy("example.com").copy(
            operations = emptyMap(),
            policyVersion = 3,
            resourceExactHosts = setOf("example.com"),
            externalExactHosts = setOf("example.com"),
            authExactHosts = setOf("example.com"),
            requestRules = listOf(
                NetworkRequestRule(
                    setOf("example.com"),
                    NetworkOperationPolicy(
                        NetworkOperations.SOURCE_READ,
                        setOf("GET", "HEAD"),
                        setOf("/"),
                        credentialed = true,
                    ),
                ),
            ),
            resourceRules = listOf(
                ResourceNetworkRule(
                    setOf("example.com"),
                    credentialed = true,
                    userAgent = "Example Browser/1.0",
                    pathPrefixes = setOf("/attachments/"),
                ),
            ),
        )

        assertEquals(3, expectedService(domainA, "example.source", policy).networkPolicy?.policyVersion)
        assertTrue(
            runCatching {
                expectedService(
                    domainA,
                    "example.source",
                    policy.copy(resourceRules = policy.resourceRules.map { it.copy(pathPrefixes = emptySet()) }),
                )
            }.isFailure,
        )
    }

    private fun snapshot(
        domain: String = BUILTIN_REPOSITORY_DOMAIN_ID,
        packageName: String = "example.extension",
        sourceId: String = "example.source",
        rootVersion: Long = 1,
        targetsVersion: Long = 1,
        expiresAt: Long = System.currentTimeMillis() + 120_000,
    ): VerifiedExtensionTrustSnapshot {
        val policy = networkPolicy("example.com")
        return VerifiedExtensionTrustSnapshot(
            repositoryDomainId = domain,
            rootVersion = rootVersion,
            targetsVersion = targetsVersion,
            expiresAtEpochMillis = expiresAt,
            policies = listOf(
                ExtensionSigningPolicy(
                    repositoryDomainId = domain,
                    packageName = packageName,
                    expectedVersionCode = 7,
                    targetLength = 4,
                    targetSha256 = "c".repeat(64),
                    lineageAnchorsSha256 = setOf(signer),
                    approvedCurrentSignersSha256 = setOf(signer),
                    sources = mapOf(sourceId to expectedService(domain, sourceId, policy)),
                ),
            ),
        )
    }

    private fun expectedService(
        domain: String,
        sourceId: String,
        policy: SourceNetworkPolicy,
        hash: String = policy.sha256(),
    ) = ExpectedSourceService(
        repositoryDomainId = domain,
        serviceClassName = "example.extension.${sourceId.replace('.', '_')}Service",
        name = "Example",
        lang = "en",
        baseUrl = "https://example.com",
        protocol = 1,
        policyHash = hash,
        networkPolicy = policy,
    )

    private fun networkPolicy(host: String) = SourceNetworkPolicy(
        exactHosts = setOf(host),
        operations = mapOf(
            NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                name = NetworkOperations.SOURCE_READ,
                methods = setOf("GET", "HEAD"),
                pathPrefixes = setOf("/"),
                credentialed = true,
            ),
        ),
        namedCapabilities = setOf(
            NamedHostCapabilities.RESOURCE_READ,
            NamedHostCapabilities.EXTERNAL_LINK,
        ),
    )
}
