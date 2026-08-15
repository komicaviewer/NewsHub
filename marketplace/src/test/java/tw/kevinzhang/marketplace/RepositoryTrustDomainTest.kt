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
}
