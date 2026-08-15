package tw.kevinzhang.newshub.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.SourceIdentityRepository
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.extension_loader.ExtensionManager
import tw.kevinzhang.newshub.auth.SourceSessionManager
import javax.inject.Inject

data class SourceWithBoards(val source: Source, val boards: List<Board>)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    extensionManager: ExtensionManager,
    private val collectionRepo: CollectionRepository,
    private val sourceIdentityRepository: SourceIdentityRepository,
    private val sessionManager: SourceSessionManager,
) : ViewModel() {

    val authStates: StateFlow<Map<String, AuthState>> = sessionManager.states

    private val _sources = MutableStateFlow<List<SourceWithBoards>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val quarantinedExtensionCount: StateFlow<Int> = extensionManager.quarantinedExtensions
        .map { it.size }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            extensionManager.quarantinedExtensions.value.size,
        )

    val collections = collectionRepo.observeCollections()

    init {
        viewModelScope.launch {
            extensionLoader.sourcesFlow.collectLatest { sources ->
                _isLoading.value = true
                _sources.value = coroutineScope {
                    sources.map { source ->
                        async {
                            if (source is AuthenticatedSource && sessionManager.stateFor(source.id) == AuthState.Unknown) {
                                if (runCatching { source.validateSession() }.getOrDefault(false)) {
                                    sessionManager.markSignedIn(source.id)
                                } else {
                                    sessionManager.markExpired(source.id)
                                }
                            }
                            SourceWithBoards(
                                source = source,
                                boards = runCatching {
                                    source.getBoardPage(BoardPageRequest()).boards
                                }.getOrDefault(emptyList()).distinctBy { it.url },
                            )
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
}
