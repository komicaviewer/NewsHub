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
import tw.kevinzhang.data.ReadingHistoryRepository
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.auth.SourceSessionManager
import tw.kevinzhang.newshub.data.PreferenceStore
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CollectionTimelineViewModel @Inject constructor(
    private val collectionRepo: CollectionRepository,
    readingHistoryRepository: ReadingHistoryRepository,
    private val extensionLoader: ExtensionLoader,
    private val sessionManager: SourceSessionManager,
    private val preferenceStore: PreferenceStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val collectionId: String = checkNotNull(savedStateHandle["collectionId"]) {
        "CollectionTimelineViewModel requires 'collectionId' in SavedStateHandle. Check navigation setup."
    }

    val rawImageSourceIds: StateFlow<Set<String>> = extensionLoader.sourcesFlow
        .map { sources ->
            sources
            .filter { it.alwaysUseRawImage }
            .map { it.id }
            .toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val sourceIconUrls: StateFlow<Map<String, String?>> = extensionLoader.sourcesFlow
        .map { sources -> sources.associate { it.id to it.iconUrl } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val sourceNames: StateFlow<Map<String, String>> = extensionLoader.sourcesFlow
        .map { sources -> sources.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val timelineDisplayMode: StateFlow<TimelineDisplayMode> = preferenceStore.observable
        .map { it.timelineDisplayMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineDisplayMode.COMPACT)

    val readThreadKeys: StateFlow<Set<Pair<String, String>>> = readingHistoryRepository
        .observeReadingHistory()
        .map { history -> history.mapTo(mutableSetOf()) { it.sourceId to it.threadId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _sourceLoadFailures = MutableStateFlow<List<SourceLoadFailure>>(emptyList())
    val sourceLoadFailures: StateFlow<List<SourceLoadFailure>> = _sourceLoadFailures.asStateFlow()

    val collectionName: StateFlow<String> = collectionRepo.observeCollections()
        .map { list -> list.firstOrNull { it.id == collectionId }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val subscriptions: StateFlow<List<BoardSubscriptionEntity>?> =
        collectionRepo.observeSubscriptions(collectionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val authenticationRequiredNotice = sessionManager.authenticationRequiredNotice

    fun consumeAuthenticationRequiredNotice(sourceId: String) {
        sessionManager.consumeAuthenticationRequiredNotice(sourceId)
    }

    fun toggleTimelineDisplayMode() {
        viewModelScope.launch {
            preferenceStore.setTimelineDisplayMode(
                if (timelineDisplayMode.value == TimelineDisplayMode.COMPACT) {
                    TimelineDisplayMode.MEDIA_FIRST
                } else {
                    TimelineDisplayMode.COMPACT
                },
            )
        }
    }

    fun clearSourceLoadFailures() {
        _sourceLoadFailures.value = emptyList()
    }

    val timelinePager: Flow<PagingData<ThreadSummary>> =
        collectionRepo.observeSubscriptions(collectionId)
            .distinctUntilChanged()
            .flatMapLatest { subs ->
                Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                    MergedTimelinePagingSource(
                        subscriptions = subs,
                        sourceResolver = { extensionLoader.getSource(it) },
                        onAuthenticationRequired = sessionManager::notifyAuthenticationRequired,
                        onSourceLoadFailures = { failures -> _sourceLoadFailures.value = failures },
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
