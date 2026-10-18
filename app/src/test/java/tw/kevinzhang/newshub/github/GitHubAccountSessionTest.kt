package tw.kevinzhang.newshub.github

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubAccountSessionTest {
    @Test fun `logout discards a late device authorization response`() = runTest {
        val tokenRequested = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val json = if (chain.request().url.encodedPath.endsWith("device/code"))
                """{"device_code":"secret-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}"""
            else {
                tokenRequested.complete(Unit)
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                """{"access_token":"late-token","token_type":"bearer","scope":"repo"}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody()).build()
        }.build()
        val http = GitHubHttp(client)
        val vault = MemoryGitHubVault()
        val session = GitHubAccountSession(GitHubOAuthClient(http, "client", { testScheduler.currentTime }), GitHubRepositoryCatalog(http), vault)
        val login = launch { session.login() }
        tokenRequested.await()
        session.logout(); release.countDown(); login.join()
        assertNull(vault.value)
        assertNull(session.state.value.account)
    }

    @Test fun `expired restored token is cleared on first attempted read`() = runTest {
        val vault = MemoryGitHubVault()
        vault.value = GitHubStoredAccount(GitHubAccount(1, "alice"), RepositoryAccessCredential.githubToken("secret"), 10)
        var now = 0L
        val http = GitHubHttp(OkHttpClient())
        val session = GitHubAccountSession(GitHubOAuthClient(http, "client"), GitHubRepositoryCatalog(http), vault, { now })
        now = 11
        assertNull(session.currentCredential())
        assertNull(vault.value)
        assertNull(session.state.value.account)
    }
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
