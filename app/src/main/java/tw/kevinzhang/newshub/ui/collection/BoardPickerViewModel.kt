package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.Source
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
    val cause: Throwable,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<BoardPickerUiState>(BoardPickerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var installedSources: List<Source> = emptyList()
    private val categoriesBySource = mutableMapOf<String, List<BoardCategory>>()
    private var query = ""
    private var selectedSourceId: String? = null
    private var selectedCategoryId: String? = null
    private var reloadJob: Job? = null
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            extensionLoader.sourcesFlow.collect { sources ->
                queryJob?.cancel()
                installedSources = sources
                categoriesBySource.keys.retainAll(sources.map(Source::id).toSet())
                if (selectedSourceId != null && sources.none { it.id == selectedSourceId }) {
                    selectedSourceId = null
                    selectedCategoryId = null
                }
                reload()
            }
        }
    }

    /** A single non-whitespace character is enough to run a remote search. */
    fun updateQuery(value: String) {
        if (query == value) return
        query = value
        reloadJob?.cancel()
        (_uiState.value as? BoardPickerUiState.Content)?.let { state ->
            _uiState.value = state.copy(query = value)
        }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            if (value.isNotBlank()) delay(SEARCH_DEBOUNCE_MILLIS)
            reload()
        }
    }

    /** null means all installed sources, whose results are loaded concurrently and kept grouped. */
    fun selectSource(sourceId: String?) {
        if (selectedSourceId == sourceId) return
        selectedSourceId = sourceId?.takeIf { id -> installedSources.any { it.id == id } }
        selectedCategoryId = null
        (_uiState.value as? BoardPickerUiState.Content)?.let { state ->
            _uiState.value = state.copy(
                selectedSourceId = selectedSourceId,
                selectedCategoryId = null,
            )
        }
        queryJob?.cancel()
        reload()
    }

    /** Categories apply only while one source is selected. */
    fun selectCategory(categoryId: String?) {
        val sourceId = selectedSourceId ?: return
        val validId = categoryId?.takeIf { id ->
            categoriesBySource[sourceId].orEmpty().any { it.id == id }
        }
        if (selectedCategoryId == validId) return
        selectedCategoryId = validId
        (_uiState.value as? BoardPickerUiState.Content)?.let { state ->
            _uiState.value = state.copy(selectedCategoryId = validId)
        }
        queryJob?.cancel()
        reload()
    }

    fun retry() {
        queryJob?.cancel()
        reload()
    }

    fun retrySource(sourceId: String) {
        if (selectedSourceId == sourceId) reload() else reloadSource(sourceId)
    }

    fun loadNextPage(sourceId: String) {
        val state = _uiState.value as? BoardPickerUiState.Content ?: return
        val group = state.sources.firstOrNull { it.source.id == sourceId } ?: return
        val token = group.nextPageToken ?: return
        if (group.isAppending) return

        val requestQuery = BoardQuery(
            text = state.query.trim(),
            categoryId = state.selectedCategoryId.takeIf { state.selectedSourceId == sourceId },
        )
        _uiState.value = state.copy(
            sources = state.sources.map {
                if (it.source.id == sourceId) it.copy(isAppending = true, appendFailure = null) else it
            },
        )
        viewModelScope.launch {
            runCatching {
                group.source.getBoardPage(
                    BoardPageRequest(
                        query = requestQuery,
                        pageToken = token,
                        pageSize = BOARD_PAGE_SIZE,
                    ),
                )
            }.onSuccess { page ->
                val current = _uiState.value as? BoardPickerUiState.Content ?: return@onSuccess
                if (!current.matches(state)) return@onSuccess
                val merged = (group.boards + page.boards).distinctBy(Board::url)
                _uiState.value = current.copy(
                    sources = current.sources.map {
                        if (it.source.id == sourceId) {
                            it.copy(
                                boards = merged,
                                nextPageToken = page.nextPageToken,
                                isAppending = false,
                                isFromCache = false,
                                appendFailure = null,
                            )
                        } else {
                            it
                        }
                    },
                )
                cache.put(sourceId, requestQuery, merged)
            }.onFailure { error ->
                val current = _uiState.value as? BoardPickerUiState.Content ?: return@onFailure
                if (!current.matches(state)) return@onFailure
                _uiState.value = current.copy(
                    sources = current.sources.map {
                        if (it.source.id == sourceId) {
                            it.copy(isAppending = false, appendFailure = error)
                        } else {
                            it
                        }
                    },
                )
            }
        }
    }

    private fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { loadFirstPages() }
    }

    private fun reloadSource(sourceId: String) {
        val state = _uiState.value as? BoardPickerUiState.Content ?: return reload()
        val source = installedSources.firstOrNull { it.id == sourceId } ?: return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val result = loadFirstPage(source, state.query, state.selectedCategoryId)
            val current = _uiState.value as? BoardPickerUiState.Content ?: return@launch
            _uiState.value = current.copy(
                sources = current.sources.map {
                    if (it.source.id == sourceId) result.toSourceWithBoards() else it
                },
                failures = current.failures.filterNot { it.source.id == sourceId } +
                    listOfNotNull(result.failure),
            )
        }
    }

    private suspend fun loadFirstPages() {
        if (installedSources.isEmpty()) {
            _uiState.value = BoardPickerUiState.NoExtensions
            return
        }

        val targetSources = selectedSourceId?.let { id ->
            installedSources.filter { it.id == id }
        } ?: installedSources
        val requestQuery = query
        val requestSourceId = selectedSourceId
        val requestCategoryId = selectedCategoryId
        val previous = _uiState.value as? BoardPickerUiState.Content
        if (previous == null) {
            _uiState.value = BoardPickerUiState.Loading
        } else {
            _uiState.value = previous.copy(
                sources = targetSources.map { source ->
                    val oldGroup = previous.sources.firstOrNull { it.source.id == source.id }
                    SourceWithBoards(
                        source = source,
                        boards = emptyList(),
                        categories = categoriesBySource[source.id] ?: oldGroup?.categories.orEmpty(),
                    )
                },
                allSources = installedSources,
                query = requestQuery,
                selectedSourceId = requestSourceId,
                selectedCategoryId = requestCategoryId,
                isRefreshing = true,
                failures = emptyList(),
            )
        }

        val results = coroutineScope {
            targetSources.map { source ->
                async { loadFirstPage(source, requestQuery, requestCategoryId) }
            }.awaitAll()
        }
        _uiState.value = boardPickerUiState(
            sources = targetSources,
            results = results,
            allSources = installedSources,
            query = requestQuery,
            selectedSourceId = requestSourceId,
            selectedCategoryId = requestCategoryId,
            allowEmptyContent = previous != null,
        )
    }

    private suspend fun loadFirstPage(
        source: Source,
        requestQuery: String,
        requestCategoryId: String?,
    ): SourceBoardLoadResult {
        val categories = categoriesBySource[source.id] ?: run {
            val remote = runCatching { source.getBoardCategories().distinctBy(BoardCategory::id) }
            if (remote.isSuccess) {
                remote.getOrThrow().also {
                    categoriesBySource[source.id] = it
                    cache.putCategories(source.id, it)
                }
            } else {
                cache.getCategories(source.id).also {
                    if (it.isNotEmpty()) categoriesBySource[source.id] = it
                }
            }
        }
        val boardQuery = BoardQuery(
            text = requestQuery.trim(),
            categoryId = requestCategoryId,
        )

        return runCatching {
            source.getBoardPage(BoardPageRequest(query = boardQuery, pageSize = BOARD_PAGE_SIZE))
        }.fold(
            onSuccess = { page ->
                val boards = page.boards.distinctBy(Board::url)
                cache.put(source.id, boardQuery, boards)
                SourceBoardLoadResult(
                    source = source,
                    boards = boards,
                    categories = categories,
                    nextPageToken = page.nextPageToken,
                )
            },
            onFailure = { error ->
                val cachedBoards = cache.get(source.id, boardQuery)
                SourceBoardLoadResult(
                    source = source,
                    boards = cachedBoards,
                    categories = categories,
                    isFromCache = cachedBoards.isNotEmpty(),
                    failure = BoardLoadFailure(source, error),
                )
            },
        )
    }

    private fun SourceBoardLoadResult.toSourceWithBoards() = SourceWithBoards(
        source = source,
        boards = boards,
        categories = categories,
        nextPageToken = nextPageToken,
        isFromCache = isFromCache,
    )

    private fun BoardPickerUiState.Content.matches(other: BoardPickerUiState.Content): Boolean =
        query == other.query &&
            selectedSourceId == other.selectedSourceId &&
            selectedCategoryId == other.selectedCategoryId
}
