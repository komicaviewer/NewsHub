package tw.kevinzhang.newshub.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.SourceIdentityRepository
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailures
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.extension_loader.ExtensionTrustPolicyProvider
import tw.kevinzhang.extension_loader.RepositoryTrustDomainState
import tw.kevinzhang.newshub.auth.SourceSessionManager
import javax.inject.Inject

sealed interface SourceBoardState {
    data object Loading : SourceBoardState
    data object LoginRequired : SourceBoardState
    data class Ready(val count: Int) : SourceBoardState
    data object EmptySuccessfully : SourceBoardState
    data class Failed(val failure: SourceFailure) : SourceBoardState
}

data class SourceWithBoards(
    val source: Source,
    val boards: List<Board>,
    val loadState: SourceBoardState = if (boards.isEmpty()) {
        SourceBoardState.EmptySuccessfully
    } else {
        SourceBoardState.Ready(boards.size)
    },
)

internal data class SourceBoardLoad(
    val boards: List<Board>,
    val state: SourceBoardState,
)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    extensionManager: ExtensionManager,
    trustPolicyProvider: ExtensionTrustPolicyProvider,
    private val collectionRepo: CollectionRepository,
    private val sourceIdentityRepository: SourceIdentityRepository,
    private val sessionManager: SourceSessionManager,
    internal val resourceProvider: HostResourceProvider,
) : ViewModel() {

    val authStates: StateFlow<Map<String, AuthState>> = sessionManager.states

    private val _sources = MutableStateFlow<List<SourceWithBoards>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val retryJobs = mutableMapOf<String, Job>()

    val quarantinedExtensionCount: StateFlow<Int> = extensionManager.quarantinedExtensions
        .map { it.size }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            extensionManager.quarantinedExtensions.value.size,
        )

    val repositoryDomainStates: StateFlow<Map<String, RepositoryTrustDomainState>> =
        trustPolicyProvider.changes
            .map { trustPolicyProvider.domainStates() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                trustPolicyProvider.domainStates(),
            )

    val collections = collectionRepo.observeCollections()

    init {
        viewModelScope.launch {
            combine(extensionLoader.sourcesFlow, sessionManager.states) { sources, authStates ->
                sources to authStates
            }.collectLatest { (sources, authStates) ->
                _isLoading.value = true
                _sources.value = coroutineScope {
                    sources.map { source ->
                        async {
                            val authState = authStates[source.id] ?: AuthState.Unknown
                            if (source is AuthenticatedSource && authState == AuthState.Unknown) {
                                if (validateSessionOrFalse(source)) {
                                    sessionManager.markSignedIn(source.id)
                                } else {
                                    sessionManager.markExpired(source.id)
                                }
                            }
                            val load = loadBoards(source, authState)
                            SourceWithBoards(source = source, boards = load.boards, loadState = load.state)
                        }
                    }.awaitAll()
                }
                _isLoading.value = false
            }
        }
    }

    fun addBoardToCollections(collectionIds: List<String>, board: Board, source: Source) {
        viewModelScope.launch {
            val identity = source.sourceIdentity ?: return@launch
            val sourceKey = sourceIdentityRepository.register(identity).sourceKey
            collectionIds.forEach { collectionId ->
                collectionRepo.addBoardSubscription(
                    collectionId = collectionId,
                    sourceKey = sourceKey,
                    boardUrl = board.url,
                    boardName = board.name,
                )
            }
        }
    }

    fun retrySource(sourceId: String) {
        val current = _sources.value.firstOrNull { it.source.id == sourceId } ?: return
        retryJobs.remove(sourceId)?.cancel()
        _sources.value = _sources.value.map {
            if (it.source.id == sourceId) it.copy(loadState = SourceBoardState.Loading) else it
        }
        retryJobs[sourceId] = viewModelScope.launch {
            try {
                val load = loadBoards(current.source, sessionManager.stateFor(sourceId))
                _sources.value = _sources.value.map {
                    if (it.source === current.source) {
                        it.copy(boards = load.boards, loadState = load.state)
                    } else {
                        it
                    }
                }
            } finally {
                retryJobs.remove(sourceId)
            }
        }
    }
}

internal suspend fun validateSessionOrFalse(source: AuthenticatedSource): Boolean = try {
    source.validateSession()
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    false
}

internal suspend fun loadBoards(
    source: Source,
    authState: AuthState = AuthState.Unknown,
): SourceBoardLoad {
    if (source is AuthenticatedSource && authState != AuthState.SignedIn) {
        return SourceBoardLoad(emptyList(), SourceBoardState.LoginRequired)
    }
    return try {
        val boards = source.getBoardPage(BoardPageRequest()).boards.distinctBy { it.url }
        SourceBoardLoad(
            boards = boards,
            state = if (boards.isEmpty()) SourceBoardState.EmptySuccessfully else SourceBoardState.Ready(boards.size),
        )
    } catch (error: CancellationException) {
        // collectLatest uses cancellation to discard work for a trust domain that was suspended or
        // revoked. Swallowing this exception would let the obsolete Source list overwrite the new
        // empty list after ExtensionManager has already quarantined the package.
        throw error
    } catch (error: Throwable) {
        SourceBoardLoad(
            boards = emptyList(),
            state = SourceBoardState.Failed(SourceFailures.fromThrowable(error, "board_page")),
        )
    }
}
