package tw.kevinzhang.marketplace

import org.junit.Assert.fail
import org.junit.Test

class MarketplaceSecurityGateTest {
    @Test
    fun `unsigned repository metadata cannot authorize downloads`() {
        try {
            MarketplaceSecurityGate.requireTrustedMetadata()
            fail("Expected Marketplace to fail closed")
        } catch (_: TrustedRepositoryUnavailableException) {
            // No unsigned index, optional hash, or custom URL can bypass this boundary.
        }
    }
}
