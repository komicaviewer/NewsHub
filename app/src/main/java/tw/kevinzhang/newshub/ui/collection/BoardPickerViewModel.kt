package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailureException
import tw.kevinzhang.extension_api.SourceFailures
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.BoardQuery
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MILLIS = 300L
private const val BOARD_PAGE_SIZE = 30

data class SourceWithBoards(
    val source: Source,
    val boards: List<Board>,
    val categories: List<BoardCategory> = emptyList(),
    val nextPageToken: String? = null,
    val isAppending: Boolean = false,
    val isFromCache: Boolean = false,
    val appendFailure: Throwable? = null,
    val catalogInfo: tw.kevinzhang.extension_api.model.BoardCatalogInfo? = null,
)

data class SelectedBoard(
    val sourceId: String,
    val sourceKey: String,
    val boardUrl: String,
    val boardName: String,
    val sourceIdentity: SourceIdentity? = null,
) {
    val key: String get() = selectedBoardKey(sourceKey, boardUrl)
}

/** A source-level board request failure, retained so the UI can explain what can be retried. */
data class BoardLoadFailure(
    val source: Source,
    val failure: SourceFailure,
)

sealed interface BoardPickerUiState {
    data object Loading : BoardPickerUiState
    data object NoExtensions : BoardPickerUiState

    data class Content(
        val sources: List<SourceWithBoards>,
        val allSources: List<Source> = sources.map(SourceWithBoards::source),
        val query: String = "",
        val selectedSourceId: String? = null,
        val selectedCategoryId: String? = null,
        val isRefreshing: Boolean = false,
        val failures: List<BoardLoadFailure> = emptyList(),
    ) : BoardPickerUiState

    data class AllSourcesFailed(
        val failures: List<BoardLoadFailure>,
        val allSources: List<Source> = failures.map(BoardLoadFailure::source),
        val query: String = "",
        val selectedSourceId: String? = null,
        val selectedCategoryId: String? = null,
    ) : BoardPickerUiState
}

internal data class SourceBoardLoadResult(
    val source: Source,
    val boards: List<Board> = emptyList(),
    val categories: List<BoardCategory> = emptyList(),
    val nextPageToken: String? = null,
    val isFromCache: Boolean = false,
    val failure: BoardLoadFailure? = null,
    val catalogInfo: tw.kevinzhang.extension_api.model.BoardCatalogInfo? = null,
)

/** Stable identity used for selection across sources whose board URLs may overlap. */
fun selectedBoardKey(sourceId: String, boardUrl: String): String = "$sourceId:$boardUrl"

fun selectedBoardCountsBySource(
    sources: List<SourceWithBoards>,
    selectedKeys: Set<String>,
): Map<String, Int> = sources.associate { (source, boards) ->
    source.id to boards.count { selectedBoardKey(source.id, it.url) in selectedKeys }
}

internal fun boardPickerUiState(
    sources: List<Source>,
    results: List<SourceBoardLoadResult>,
    allSources: List<Source> = sources,
    query: String = "",
    selectedSourceId: String? = null,
    selectedCategoryId: String? = null,
    allowEmptyContent: Boolean = false,
): BoardPickerUiState {
    if (sources.isEmpty()) return BoardPickerUiState.NoExtensions

    val failures = results.mapNotNull(SourceBoardLoadResult::failure)
    val hasVisibleResult = results.any { it.failure == null || it.boards.isNotEmpty() }
    if (!hasVisibleResult && !allowEmptyContent) {
        return BoardPickerUiState.AllSourcesFailed(
            failures = failures,
            allSources = allSources,
            query = query,
            selectedSourceId = selectedSourceId,
            selectedCategoryId = selectedCategoryId,
        )
    }

    return BoardPickerUiState.Content(
        sources = results.map {
            SourceWithBoards(
                source = it.source,
                boards = it.boards,
                categories = it.categories,
                nextPageToken = it.nextPageToken,
                isFromCache = it.isFromCache,
                catalogInfo = it.catalogInfo,
            )
        },
        allSources = allSources,
        query = query,
        selectedSourceId = selectedSourceId,
        selectedCategoryId = selectedCategoryId,
        failures = failures,
    )
}

@HiltViewModel
class BoardPickerViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val cache: BoardCatalogCache,
    private val sessionManager: tw.kevinzhang.newshub.auth.SourceSessionManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<BoardPickerUiState>(BoardPickerUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var installedSources: List<Source> = emptyList()
    private val controllers = mutableMapOf<String, tw.kevinzhang.newshub.ui.boards.BoardCatalogController>()
    private var query = ""
    private var selectedSourceId: String? = null
    private var selectedCategoryId: String? = null

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(extensionLoader.sourcesFlow, sessionManager.catalogContexts) { sources, contexts ->
                sources to contexts
            }.collectLatest { (sources, contexts) ->
                controllers.values.forEach { it.close() }
                controllers.clear()
                installedSources = sources
                if (sources.none { it.id == selectedSourceId }) {
                    selectedSourceId = null
                    selectedCategoryId = null
                }
                kotlinx.coroutines.coroutineScope {
                    sources.forEach { source ->
                        val context = contexts[source.id] ?: return@forEach
                        val controller = tw.kevinzhang.newshub.ui.boards.BoardCatalogController(source, context, cache, this)
                        controllers[source.id] = controller
                        controller.setQuery(requestQuery(source.id))
                        launch { controller.state.collect { publish() } }
                    }
                    publish()
                    kotlinx.coroutines.awaitCancellation()
                }
            }
        }
    }
    fun updateQuery(value: String) {
        if (query == value) return
        query = value
        updateRequests()
    }
    fun selectSource(sourceId: String?) {
        selectedSourceId = sourceId
        selectedCategoryId = null
        updateRequests()
    }
    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        updateRequests()
    }
    fun retry() { targetControllers().forEach { it.retry() } }
    fun refresh() { targetControllers().forEach { it.refresh() } }
    fun retrySource(sourceId: String) { controllers[sourceId]?.retry() }
    fun loadNextPage(sourceId: String) { controllers[sourceId]?.loadMore() }
    private fun requestQuery(sourceId: String) = BoardQuery(query.trim(), selectedCategoryId.takeIf { selectedSourceId == sourceId })
    private fun targetControllers() = controllers.filterKeys { selectedSourceId == null || it == selectedSourceId }.values
    private fun updateRequests() {
        controllers.forEach { (id, controller) ->
            if (selectedSourceId == null || selectedSourceId == id) controller.setQuery(requestQuery(id))
        }
        publish()
    }
    private fun publish() {
        val sources = installedSources.filter { selectedSourceId == null || it.id == selectedSourceId }
        val results = sources.map { source ->
            val state = controllers[source.id]?.state?.value
            SourceBoardLoadResult(source, state?.boards.orEmpty(), state?.categories.orEmpty(), state?.nextPageToken,
                state?.isRecentFallback ?: false,
                state?.firstPageFailure?.let { BoardLoadFailure(source, it) },
                state?.catalogInfo,
            )
        }
        val base = boardPickerUiState(sources, results, installedSources, query, selectedSourceId, selectedCategoryId, true)
        _uiState.value = if (base is BoardPickerUiState.Content) base.copy(
            isRefreshing = targetControllers().any { it.state.value.firstPageLoading },
            sources = base.sources.map { group ->
                val state = controllers[group.source.id]?.state?.value
                group.copy(isAppending = state?.moreLoading ?: false,
                    appendFailure = state?.moreFailure?.let(::SourceFailureException))
            },
        ) else base
    }
}
