package tw.kevinzhang.newshub.github

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.marketplace.*
import tw.kevinzhang.newshub.repo.RepositoryAccessStatusStore

class GitHubRepositoryAccessMonitorTest {
    @Test fun `logout pauses only account sources and login restores readable without altering trust state`() = runTest {
        val statuses = RepositoryAccessStatusStore()
        val state = MutableStateFlow(GitHubSessionState())
        val base = RepositoryTrustDomain("00000000-0000-4000-8000-000000000001", "https://github.com/org/repo",
            RepositoryTrustMode.USER_PINNED, RepositoryDomainState.ACTIVE, 1, setOf("a".repeat(64)),
            RepositoryAccessDescriptor.githubContents(credentialBinding = RepositoryCredentialBinding.GITHUB_ACCOUNT))
        val suspended = base.copy(id = "00000000-0000-4000-8000-000000000002", state = RepositoryDomainState.SUSPENDED)
        val pat = base.copy(id = "00000000-0000-4000-8000-000000000003", access = RepositoryAccessDescriptor.githubContents())
        val seen = mutableListOf<String>()
        GitHubRepositoryAccessMonitor(backgroundScope, state, MutableStateFlow(listOf(base, suspended, pat)),
            { RepositoryAccessCredential.githubToken("token") }, { domain, _ -> seen += domain.id }, statuses)
        runCurrent()
        assertTrue(base.id in statuses.requiredDomainIds.value)
        assertFalse(pat.id in statuses.requiredDomainIds.value)
        state.value = GitHubSessionState(account = GitHubAccount(2, "other"))
        runCurrent()
        assertFalse(base.id in statuses.requiredDomainIds.value)
        assertEquals(listOf(base.id), seen)
        assertEquals(RepositoryDomainState.SUSPENDED, suspended.state)
    }
}
