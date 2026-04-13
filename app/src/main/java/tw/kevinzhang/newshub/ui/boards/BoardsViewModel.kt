package tw.kevinzhang.newshub.ui.boards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.CollectionRepository
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.auth.AppCookieJar
import tw.kevinzhang.newshub.auth.AuthRepository
import tw.kevinzhang.newshub.auth.LoginStatus
import javax.inject.Inject

data class SourceWithBoards(val source: Source, val boards: List<Board>)

@HiltViewModel
class BoardsViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val collectionRepo: CollectionRepository,
    private val authRepository: AuthRepository,
    private val cookieJar: AppCookieJar,
) : ViewModel() {

    val loginStatuses: StateFlow<Map<String, LoginStatus>> = authRepository.loginStatuses

    private val _sources = MutableStateFlow<List<SourceWithBoards>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val collections = collectionRepo.observeCollections()

    init {
        viewModelScope.launch {
            extensionLoader.sourcesFlow.collectLatest { sources ->
                _isLoading.value = true
                _sources.value = sources.map { source ->
                    // Restore login status from persisted cookies on app restart.
                    if (source.needsLogin) {
                        val cookieUrl = authRepository.cookieUrls.value[source.id]
                        if (cookieUrl != null && cookieJar.hasCookiesForUrl(cookieUrl)) {
                            authRepository.setLoggedIn(source.id, cookieUrl)
                        }
                    }
                    SourceWithBoards(
                        source = source,
                        boards = runCatching { source.getBoards() }.getOrDefault(emptyList()).distinctBy { it.url },
                    )
                }
                _isLoading.value = false
            }
        }
    }

    fun addBoardToCollections(collectionIds: List<String>, board: Board, source: Source) {
        viewModelScope.launch {
            collectionIds.forEach { collectionId ->
                collectionRepo.addBoardSubscription(
                    collectionId = collectionId,
                    sourceId = source.id,
                    boardUrl = board.url,
                    boardName = board.name,
                )
            }
        }
    }
}
