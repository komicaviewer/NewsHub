package tw.kevinzhang.newshub.github

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tw.kevinzhang.marketplace.RepositoryAccessCredential

data class GitHubSearchState(val query: String = "", val results: List<GitHubRepo> = emptyList(),
    val loading: Boolean = false, val complete: Boolean = false, val error: String? = null)

/** A single, cancellable paged directory per logged-in session. */
class GitHubRepositorySearch(private val scope: CoroutineScope, session: StateFlow<GitHubSessionState>,
    private val credential: () -> RepositoryAccessCredential?,
    private val fetch: suspend (RepositoryAccessCredential, Int) -> GitHubRepoPage) {
    private val _state = MutableStateFlow(GitHubSearchState())
    val state = _state.asStateFlow()
    private val directory = linkedMapOf<Long, GitHubRepo>()
    private var nextPage: Int? = 1
    private var job: Job? = null
    private var account = session.value.account
    private var generation = 0
    init {
        scope.launch {
            session.map { it.account }.distinctUntilChanged().collect {
                if (it == account) return@collect
                account = it; generation++
                job?.cancel(); directory.clear(); nextPage = 1
                _state.value = GitHubSearchState()
            }
        }
    }
    fun query(value: String) {
        _state.value = _state.value.copy(query = value.take(256), error = null)
        publish()
        if (value.isNotBlank()) loadMore()
    }
    fun loadMore() {
        if (job?.isActive == true || nextPage == null) return
        val token = credential() ?: return
        val stamp = generation
        job = scope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                do {
                    val page = nextPage ?: break
                    val result = fetch(token, page)
                    token.ensureCurrent(); ensureActive()
                    if (stamp != generation) break
                    result.items.forEach { directory[it.id] = it }
                    nextPage = result.nextPage
                    publish()
                    // Avoid bursts for directory scans, allow immediate cancellation.
                    if (nextPage != null && _state.value.query.isNotBlank()) delay(150)
                } while (nextPage != null && _state.value.query.isNotBlank())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (stamp == generation) _state.value = _state.value.copy(error = if (e is GitHubFailure) e.message else "無法載入 repositories，請重試。")
            } finally { if (stamp == generation) _state.value = _state.value.copy(loading = false) }
        }
    }
    private fun publish() {
        _state.value = _state.value.copy(results = directory.values.filter { it.matches(_state.value.query) }, complete = nextPage == null)
    }
}
