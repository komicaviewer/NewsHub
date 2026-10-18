package tw.kevinzhang.newshub.github

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubRepositorySearchTest {
    @Test fun `search reaches later pages and logout clears private results`() = runTest {
        val state = MutableStateFlow(GitHubSessionState(GitHubAccount(1, "alice")))
        val workers = CoroutineScope(coroutineContext + SupervisorJob())
        val search = GitHubRepositorySearch(workers, state, { RepositoryAccessCredential.githubToken("secret") }) { _, page ->
            GitHubRepoPage(listOf(GitHubRepo(page.toLong(), "org", if (page == 1) "other" else "wanted", "main", true, null)), if (page == 1) 2 else null)
        }
        search.query("wanted")
        advanceUntilIdle()
        assertEquals(listOf("wanted"), search.state.value.results.map { it.name })
        assertTrue(search.state.value.complete)
        state.value = GitHubSessionState()
        advanceUntilIdle()
        assertTrue(search.state.value.results.isEmpty())
        workers.cancel()
    }
}
