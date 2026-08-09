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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.ReadingHistoryRepository
import tw.kevinzhang.data.SourceIdentityRepository
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
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
    private val sourceIdentityRepository: SourceIdentityRepository,
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
        .map { history -> history.mapTo(mutableSetOf()) { it.history.sourceKey to it.history.threadId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _sourceLoadFailures = MutableStateFlow<List<SourceLoadFailure>>(emptyList())
    val sourceLoadFailures: StateFlow<List<SourceLoadFailure>> = _sourceLoadFailures.asStateFlow()

    val collectionName: StateFlow<String> = collectionRepo.observeCollections()
        .map { list -> list.firstOrNull { it.id == collectionId }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val subscriptions: StateFlow<List<BoardSubscriptionRecord>?> =
        collectionRepo.observeSubscriptions(collectionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sources represented by the current collection's board subscriptions, in display order. */
    val availableSourceIds: StateFlow<List<String>> = subscriptions
        .map { subscriptions ->
            subscriptions.orEmpty().map { it.sourceIdentity.sourceId }.distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val savedSelectedSourceId = preferenceStore
        .observeCollectionSelectedSourceId(collectionId)

    /**
     * The single canonical source filter. Both the filter chips and the Pager consume this flow,
     * so a persisted preference cannot briefly disagree with the timeline it controls. `null`
     * means all subscribed sources; stale persisted selections are cleared after subscriptions
     * become available.
     */
    private val effectiveSelectedSourceId: StateFlow<String?> = combine(
        savedSelectedSourceId,
        subscriptions,
    ) { savedSourceId, currentSubscriptions ->
        SourceSelection(
            savedSourceId = savedSourceId,
            availableSourceIds = currentSubscriptions
                ?.map { it.sourceIdentity.sourceId }
                ?.toSet(),
        )
    }
        .onEach { selection ->
            if (
                selection.savedSourceId != null &&
                selection.availableSourceIds != null &&
                resolveSelectedSourceId(
                    savedSourceId = selection.savedSourceId,
                    availableSourceIds = selection.availableSourceIds,
                ) == null
            ) {
                preferenceStore.setCollectionSelectedSourceId(collectionId, null)
            }
        }
        .map { selection ->
            resolveSelectedSourceId(
                savedSourceId = selection.savedSourceId,
                availableSourceIds = selection.availableSourceIds,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedSourceId: StateFlow<String?> = effectiveSelectedSourceId

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

    fun selectSource(sourceId: String?) {
        _sourceLoadFailures.value = emptyList()
        viewModelScope.launch {
            preferenceStore.setCollectionSelectedSourceId(collectionId, sourceId)
        }
    }

    val timelinePager: Flow<PagingData<ThreadSummary>> =
        combine(
            subscriptions.filterNotNull(),
            effectiveSelectedSourceId,
        ) { currentSubscriptions, selectedSourceId ->
            filterSubscriptionsBySource(currentSubscriptions, selectedSourceId)
        }
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
            val source = extensionLoader.getSource(sourceId) ?: return@launch
            val identity = source.sourceIdentity ?: return@launch
            val sourceKey = sourceIdentityRepository.register(identity).sourceKey
            collectionRepo.addBoardSubscription(
                collectionId = collectionId,
                sourceKey = sourceKey,
                boardUrl = boardUrl,
                boardName = boardName,
            )
        }
    }
}

private data class SourceSelection(
    val savedSourceId: String?,
    val availableSourceIds: Set<String>?,
)

/** Returns the effective filter. A null available set means subscriptions are still loading. */
internal fun resolveSelectedSourceId(
    savedSourceId: String?,
    availableSourceIds: Set<String>?,
): String? = when {
    savedSourceId == null -> null
    availableSourceIds == null -> savedSourceId
    savedSourceId in availableSourceIds -> savedSourceId
    else -> null
}

internal fun filterSubscriptionsBySource(
    subscriptions: List<BoardSubscriptionRecord>,
    selectedSourceId: String?,
): List<BoardSubscriptionRecord> = when (selectedSourceId) {
    null -> subscriptions
    else -> subscriptions.filter { it.sourceIdentity.sourceId == selectedSourceId }
}
