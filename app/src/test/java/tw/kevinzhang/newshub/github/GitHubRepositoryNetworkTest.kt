package tw.kevinzhang.newshub.github

import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.newshub.di.NetworkModule

class GitHubRepositoryNetworkTest {
    @Test fun `private metadata client never inherits debug URL logging`() {
        val client = NetworkModule.provideRepositoryHttpClient()
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
        assertTrue(client.callTimeoutMillis > 0)
    }
}
