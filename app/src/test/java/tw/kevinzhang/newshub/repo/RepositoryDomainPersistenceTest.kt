package tw.kevinzhang.newshub.repo

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryAccessDescriptor
import tw.kevinzhang.marketplace.RepositoryAccessKind
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustMode

class RepositoryDomainPersistenceTest {
    @Test
    fun `domain records round trip every trust state and sorted fingerprints`() {
        val domains = RepositoryDomainState.entries.mapIndexed { index, state ->
            RepositoryTrustDomain(
                id = "00000000-0000-4000-8000-${(index + 10).toString().padStart(12, '0')}",
                canonicalBaseUrl = "https://repo$index.example.test/extensions",
                trustMode = RepositoryTrustMode.USER_PINNED,
                state = state,
                rootThreshold = 1,
                rootKeyFingerprints = linkedSetOf("b".repeat(64), "a".repeat(64)),
            )
        }

        assertEquals(domains, decodeRepositoryDomains(encodeRepositoryDomains(domains)))
        val encoded = encodeRepositoryDomains(domains)
        check(encoded.indexOf("a".repeat(64)) < encoded.indexOf("b".repeat(64)))
    }

    @Test
    fun `private GitHub access descriptor persists without a credential`() {
        val domain = RepositoryTrustDomain(
            id = "00000000-0000-4000-8000-000000000099",
            canonicalBaseUrl = "https://github.com/example/private-extensions",
            trustMode = RepositoryTrustMode.USER_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = 1,
            rootKeyFingerprints = setOf("a".repeat(64)),
            access = RepositoryAccessDescriptor.githubContents("release"),
        )

        val encoded = encodeRepositoryDomains(listOf(domain))

        assertEquals(listOf(domain), decodeRepositoryDomains(encoded))
        check(!encoded.contains("token", ignoreCase = true))
        check(encoded.contains(RepositoryAccessKind.GITHUB_CONTENTS.name))
    }

    @Test
    fun `legacy domain without access descriptor migrates to public HTTPS`() {
        val legacy = """[{"id":"00000000-0000-4000-8000-000000000098","canonicalBaseUrl":"https://repo.example.test/extensions","trustMode":"USER_PINNED","state":"ACTIVE","rootThreshold":1,"rootKeyFingerprints":["${"a".repeat(64)}"]}]"""

        val decoded = decodeRepositoryDomains(legacy).single()

        assertEquals(RepositoryAccessKind.PUBLIC_HTTPS, decoded.access.kind)
    }
}
