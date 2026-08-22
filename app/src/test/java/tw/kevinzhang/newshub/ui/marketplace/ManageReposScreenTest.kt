package tw.kevinzhang.newshub.ui.marketplace

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryAccessDescriptor
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustMode

class ManageReposScreenTest {
    @Test
    fun `management UI exposes trust mode and all repository states in Chinese`() {
        val domain = RepositoryTrustDomain(
            id = "99999999-9999-4999-8999-999999999999",
            canonicalBaseUrl = "https://repo.example.test/extensions",
            trustMode = RepositoryTrustMode.USER_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = 1,
            rootKeyFingerprints = setOf("a".repeat(64)),
        )
        assertEquals("使用者信任", domain.trustModeLabel())
        assertEquals("正常", domain.stateLabel())
        assertEquals("已過期", domain.copy(state = RepositoryDomainState.EXPIRED).stateLabel())
        assertEquals("已暫停", domain.copy(state = RepositoryDomainState.SUSPENDED).stateLabel())
        assertEquals("已撤銷", domain.copy(state = RepositoryDomainState.REVOKED).stateLabel())
        assertEquals("公開 HTTPS", domain.accessLabel())
        assertEquals(
            "GitHub private (main)",
            domain.copy(
                canonicalBaseUrl = "https://github.com/example/private-extensions",
                access = RepositoryAccessDescriptor.githubContents(),
            ).accessLabel(),
        )
    }
}
