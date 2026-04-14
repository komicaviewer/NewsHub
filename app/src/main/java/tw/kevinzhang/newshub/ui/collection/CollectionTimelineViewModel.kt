package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionTimelineViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    private val extensionLoader: ExtensionLoader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val collectionId: String = checkNotNull(savedStateHandle["collectionId"]) {
        "CollectionTimelineViewModel requires 'collectionId' in SavedStateHandle. Check navigation setup."
    }

    val rawImageSourceIds: StateFlow<Set<String>> = MutableStateFlow(
        extensionLoader.getAllSources()
            .filter { it.alwaysUseRawImage }
            .map { it.id }
            .toSet()
    ).asStateFlow()

    val sourceIconUrls: StateFlow<Map<String, String?>> = MutableStateFlow(
        extensionLoader.getAllSources().associate { it.id to it.iconUrl }
    ).asStateFlow()

    val collectionName: StateFlow<String> = collectionRepo.observeCollections()
        .map { list -> list.firstOrNull { it.id == collectionId }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val subscriptions: StateFlow<List<BoardSubscriptionEntity>?> =
        collectionRepo.observeSubscriptions(collectionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val timelinePager: Flow<PagingData<ThreadSummary>> =
        collectionRepo.observeSubscriptions(collectionId)
            .distinctUntilChanged()
            .flatMapLatest { subs ->
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    MergedTimelinePagingSource(
                        subscriptions = subs,
                        sourceResolver = { extensionLoader.getSource(it) },
                    )
                }.flow
            }
            .cachedIn(viewModelScope)

    fun addBoardSubscription(sourceId: String, boardUrl: String, boardName: String) {
        viewModelScope.launch {
            collectionRepo.addBoardSubscription(
                collectionId = collectionId,
                sourceId = sourceId,
                boardUrl = boardUrl,
                boardName = boardName,
            )
        }
    }
}
