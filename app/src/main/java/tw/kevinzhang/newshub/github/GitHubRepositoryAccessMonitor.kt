package tw.kevinzhang.newshub.github

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.kevinzhang.marketplace.*
import tw.kevinzhang.newshub.repo.RepositoryAccessStatusStore

/** Access health is distinct from trust state; never resumes a user-suspended or revoked source. */
class GitHubRepositoryAccessMonitor(scope: CoroutineScope, session: StateFlow<GitHubSessionState>,
    domains: Flow<List<RepositoryTrustDomain>>, credential: () -> RepositoryAccessCredential?,
    verify: suspend (RepositoryTrustDomain, RepositoryAccessCredential) -> Unit,
    statuses: RepositoryAccessStatusStore) {
    init {
        scope.launch {
            combine(session, domains) { login, sources -> login to sources }.collectLatest { (login, sources) ->
                val accountSources = sources.filter { it.access.credentialBinding == RepositoryCredentialBinding.GITHUB_ACCOUNT }
                accountSources.forEach { statuses.requireAccess(it.id) }
                if (login.account == null) return@collectLatest
                val token = credential() ?: return@collectLatest
                accountSources.filter { it.state == RepositoryDomainState.ACTIVE }.forEach { source ->
                    try {
                        token.authorized { verify(source, token) }
                        token.ensureCurrent()
                        statuses.clear(source.id)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Leave access pending; transient failures are retryable. */ }
                }
            }
        }
    }
}
