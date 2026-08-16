package tw.kevinzhang.newshub.repo

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryDomainState
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
}
