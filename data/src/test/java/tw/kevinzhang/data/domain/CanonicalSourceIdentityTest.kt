package tw.kevinzhang.data.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.extension_api.BUILTIN_REPOSITORY_DOMAIN_ID
import tw.kevinzhang.extension_api.SourceIdentity

class CanonicalSourceIdentityTest {
    @Test
    fun `known official legacy source resolves to pinned canonical identity`() {
        val identity = CanonicalSourceIdentities.fromLegacySourceId(
            "tw.kevinzhang.newshub.extension.hackernews",
        )

        assertEquals(SourceResolution.OFFICIAL, identity.resolution)
        assertEquals("tw.kevinzhang.newshub.extension.hackernews", identity.packageName)
        assertEquals(
            "3df4717435423d5ba7adfed43a22a6e18bbeadc8d509d0bea94d82c7b0f2998d",
            identity.signerSha256,
        )
        assertEquals(
            "ef25ad434d63bd66ad885e1d6f9ff82840ee9a3cd67834c2ee78477e71e77425",
            identity.sourceKey,
        )
        assertEquals(BUILTIN_REPOSITORY_DOMAIN_ID, identity.repositoryDomainId)
    }

    @Test
    fun `unknown legacy source remains explicitly unresolved`() {
        val identity = CanonicalSourceIdentities.fromLegacySourceId("attacker.source")

        assertEquals(SourceResolution.UNRESOLVED, identity.resolution)
        assertEquals("attacker.source", identity.sourceId)
        assertNull(identity.packageName)
        assertNull(identity.signerSha256)
        assertNull(identity.repositoryDomainId)
        assertEquals("unresolved:", identity.sourceKey.take(11))
    }

    @Test
    fun `verified runtime package cannot claim migrated identity by reusing its source id`() {
        val migrated = CanonicalSourceIdentities.fromLegacySourceId(
            "tw.kevinzhang.newshub.extension.hackernews",
        )
        val identity = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "attacker.package",
                signerSha256 = migrated.signerSha256!!,
                sourceId = "tw.kevinzhang.newshub.extension.hackernews",
            ),
        )

        assertEquals(SourceResolution.OFFICIAL, identity.resolution)
        assertNotEquals(migrated.sourceKey, identity.sourceKey)
    }

    @Test
    fun `approved signer rotation does not change canonical source key`() {
        val lineageRoot = "1".repeat(64)
        val before = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "official.package",
                signerSha256 = lineageRoot,
                sourceId = "official.source",
                currentSignerSha256 = "2".repeat(64),
            ),
        )
        val after = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "official.package",
                signerSha256 = lineageRoot,
                sourceId = "official.source",
                currentSignerSha256 = "3".repeat(64),
            ),
        )

        assertEquals(before, after)
    }

    @Test
    fun `same package lineage and source in different repository domains have distinct identities`() {
        val first = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "shared.package",
                signerSha256 = "1".repeat(64),
                sourceId = "shared.source",
                repositoryDomainId = "00000000-0000-0000-0000-000000000001",
            ),
        )
        val second = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "shared.package",
                signerSha256 = "1".repeat(64),
                sourceId = "shared.source",
                repositoryDomainId = "00000000-0000-0000-0000-000000000002",
            ),
        )

        assertNotEquals(first.sourceKey, second.sourceKey)
        assertNotEquals(first.repositoryDomainId, second.repositoryDomainId)
    }

    @Test
    fun `verified new extension cannot claim migrated unresolved rows by source id`() {
        val migrated = CanonicalSourceIdentities.fromLegacySourceId("unknown.source")
        val runtime = CanonicalSourceIdentities.fromRuntimeIdentity(
            SourceIdentity(
                packageName = "unknown.package",
                signerSha256 = "a".repeat(64),
                sourceId = "unknown.source",
            ),
        )

        assertEquals(SourceResolution.UNRESOLVED, migrated.resolution)
        assertEquals(SourceResolution.OFFICIAL, runtime.resolution)
        assertNotEquals(migrated.sourceKey, runtime.sourceKey)
    }
}
