package tw.kevinzhang.newshub.github

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubAccountSessionTest {
    @Test fun `restored credential expires and logout invalidates outstanding lease`() = runTest {
        val vault = MemoryGitHubVault()
        vault.value = GitHubStoredAccount(GitHubAccount(1, "alice"), RepositoryAccessCredential.githubToken("secret"), 10000)
        var now = 0L
        val http = GitHubHttp(okhttp3.OkHttpClient())
        val session = GitHubAccountSession(GitHubOAuthClient(http, "client"), GitHubRepositoryCatalog(http), vault, { now })
        val lease = session.currentCredential()!!
        assertEquals("alice", session.state.value.account?.login)
        session.logout()
        assertNull(vault.value)
        assertNull(session.currentCredential())
        try { lease.ensureCurrent(); fail() } catch (_: Exception) { }
        assertNull(session.state.value.account)
        assertFalse(lease.toString().contains("secret"))
    }
}

internal class MemoryGitHubVault : GitHubAccountStorage {
    var value: GitHubStoredAccount? = null
    override fun read() = value
    override fun write(account: GitHubStoredAccount) { value = account }
    override fun clear() { value = null }
}
