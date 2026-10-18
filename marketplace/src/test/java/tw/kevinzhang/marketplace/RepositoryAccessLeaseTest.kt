package tw.kevinzhang.marketplace

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RepositoryAccessLeaseTest {
    @Test fun `logout between downloaded result and installer prevents installation`() = runBlocking {
        val lifetime = Job()
        val credential = RepositoryAccessCredential.githubToken("secret").leased(lifetime) {}
        var installed = false
        try {
            credential.authorized {
                lifetime.cancel()
                yield()
                installed = true
            }
            fail()
        } catch (_: CancellationException) { }
        assertFalse(installed)
    }
}
