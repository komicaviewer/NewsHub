package tw.kevinzhang.marketplace

import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryTrustDomainsConfigTest {
    @Test
    fun `official base url defaults to production`() {
        assertEquals(
            "https://raw.githubusercontent.com/komicaviewer/extensions/main",
            RepositoryTrustDomains.OFFICIAL_BASE_URL,
        )
        assertEquals(BuildConfig.OFFICIAL_TRUST_BASE_URL, RepositoryTrustDomains.OFFICIAL_BASE_URL)
    }
}
