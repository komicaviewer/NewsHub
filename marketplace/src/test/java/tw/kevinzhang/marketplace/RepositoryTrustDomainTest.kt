package tw.kevinzhang.marketplace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryTrustDomainTest {
    @Test
    fun `canonical repository accepts fixed HTTPS base path`() {
        assertEquals(
            "https://repo.example.test/distribution",
            canonicalRepositoryBaseUrl("https://REPO.example.test/distribution/").toString().trimEnd('/'),
        )
    }

    @Test
    fun `repository rejects mutable or unsafe origins`() {
        listOf(
            "http://repo.example.test",
            "https://user@repo.example.test",
            "https://repo.example.test/path?channel=stable",
            "https://repo.example.test/path#root",
            "https://repo.example.test:8443/path",
        ).forEach { value ->
            assertThrows(TrustedMetadataException::class.java) { canonicalRepositoryBaseUrl(value) }
        }
    }

    @Test
    fun `GitHub access requires an exact repository URL and a safe revision`() {
        val access = RepositoryAccessDescriptor.githubContents("release/stable")
        RepositoryTrustDomain(
            id = "55555555-5555-4555-8555-555555555555",
            canonicalBaseUrl = "https://github.com/acme/extensions",
            trustMode = RepositoryTrustMode.USER_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = 1,
            rootKeyFingerprints = setOf("a".repeat(64)),
            access = access,
        )

        listOf(
            "https://github.com/acme",
            "https://github.com/acme/extensions/extra",
            "https://raw.githubusercontent.com/acme/extensions/main",
        ).forEach { invalid ->
            assertThrows(TrustedMetadataException::class.java) {
                RepositoryTrustDomain(
                    id = "55555555-5555-4555-8555-555555555555",
                    canonicalBaseUrl = invalid,
                    trustMode = RepositoryTrustMode.USER_PINNED,
                    state = RepositoryDomainState.ACTIVE,
                    rootThreshold = 1,
                    rootKeyFingerprints = setOf("a".repeat(64)),
                    access = access,
                )
            }
        }
        listOf("", "../main", "/main", "main?raw=1").forEach { revision ->
            assertThrows(IllegalArgumentException::class.java) {
                RepositoryAccessDescriptor.githubContents(revision)
            }
        }
    }
}
