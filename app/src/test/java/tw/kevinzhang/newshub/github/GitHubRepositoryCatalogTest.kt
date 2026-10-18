package tw.kevinzhang.newshub.github

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubRepositoryCatalogTest {
    @Test fun `pagination preserves private repository and non main default branch`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("repo-token", chain.request().header("Authorization")?.removePrefix("Bearer "))
            assertEquals("100", chain.request().url.queryParameter("per_page"))
            val second = chain.request().url.queryParameter("page") == "2"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .apply { if (!second) header("Link", "<https://api.github.com/user/repos?page=2>; rel=\"next\"") }
                .body(if (second) """[{"id":2,"owner":{"login":"org"},"name":"wanted","default_branch":"release/stable","private":true,"description":null}]""".toResponseBody()
                    else "[]".toResponseBody()).build()
        }.build()
        val catalog = GitHubRepositoryCatalog(GitHubHttp(client))
        val token = RepositoryAccessCredential.githubToken("repo-token")
        assertEquals(2, catalog.listRepositories(token, 1).nextPage)
        val page = catalog.listRepositories(token, 2)
        assertNull(page.nextPage)
        assertEquals("release/stable", page.items.single().defaultBranch)
        assertTrue(page.items.single().private)
    }

    @Test fun `rate limit is distinct from inaccessible and does not expose response body`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("No")
                .header("X-RateLimit-Remaining", "0").body("secret-server-detail".toResponseBody()).build()
        }.build()
        try { GitHubRepositoryCatalog(GitHubHttp(client)).listRepositories(RepositoryAccessCredential.githubToken("token"), 1); fail() }
        catch (e: GitHubFailure) { assertEquals("rate_limit", e.kind); assertFalse(e.toString().contains("secret-server-detail")) }
    }
}
