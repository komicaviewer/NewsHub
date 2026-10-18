package tw.kevinzhang.newshub.github

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.kevinzhang.marketplace.RepositoryAccessCredential

data class GitHubSessionState(val account: GitHubAccount? = null, val userCode: String? = null,
    val verificationUri: String? = null, val expiresAt: Long? = null, val busy: Boolean = false, val error: String? = null)

class GitHubAccountSession(private val oauth: GitHubOAuthClient, private val catalog: GitHubRepositoryCatalog,
    private val vault: GitHubAccountStorage, private val now: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private var stored: GitHubStoredAccount? = vault.read()?.takeIf { it.expiresAt == null || it.expiresAt > now() }
    private var lifetime: Job = SupervisorJob()
    private var loginJob: Job? = null
    private var epoch = 0L
    val configured get() = oauth.clientId.isNotBlank()
    val generation get() = synchronized(lock) { epoch }
    private val _state = MutableStateFlow(GitHubSessionState(account = stored?.account))
    val state = _state.asStateFlow()

    suspend fun login() {
        val job = currentCoroutineContext()[Job]
        val stamp = synchronized(lock) {
            loginJob?.cancel(); loginJob = job; ++epoch
            _state.value = GitHubSessionState(busy = true)
            epoch
        }
        try {
            val challenge = oauth.start()
            synchronized(lock) {
                check(stamp == epoch)
                _state.value = GitHubSessionState(userCode = challenge.userCode, verificationUri = challenge.verificationUri,
                    expiresAt = challenge.expiresAt, busy = true)
            }
            val token = oauth.awaitAuthorization(challenge)
            val account = catalog.getAccount(token.credential)
            currentCoroutineContext().ensureActive()
            synchronized(lock) {
                check(stamp == epoch)
                val value = GitHubStoredAccount(account, token.credential, token.expiresAt)
                vault.write(value)
                lifetime.cancel(); lifetime = SupervisorJob()
                stored = value
                _state.value = GitHubSessionState(account = account)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            synchronized(lock) { if (stamp == epoch) _state.value = GitHubSessionState(error = if (e is GitHubFailure) e.message else "無法登入 GitHub，請重試。") }
        } finally { synchronized(lock) { if (loginJob === job) loginJob = null } }
    }

    fun cancelLogin() = synchronized(lock) {
        ++epoch; loginJob?.cancel(); loginJob = null
        _state.value = GitHubSessionState(account = stored?.account)
    }

    fun logout() = synchronized(lock) {
        ++epoch; lifetime.cancel(); loginJob?.cancel(); loginJob = null
        stored = null
        _state.value = GitHubSessionState()
        vault.clear()
    }

    fun currentCredential(): RepositoryAccessCredential? = synchronized(lock) {
        val value = stored ?: return@synchronized null
        if (value.expiresAt != null && value.expiresAt <= now()) {
            logout(); _state.value = GitHubSessionState(error = "GitHub 登入已到期，請重新登入。")
            return@synchronized null
        }
        val stamp = epoch
        value.credential.leased(lifetime) {
            synchronized(lock) {
                check(stamp == epoch && stored === value && (value.expiresAt == null || value.expiresAt > now())) { "GitHub login expired" }
            }
        }
    }
}
