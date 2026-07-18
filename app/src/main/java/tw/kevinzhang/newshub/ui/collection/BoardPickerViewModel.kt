package tw.kevinzhang.newshub.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

data class SourceWithBoards(val source: Source, val boards: List<Board>)

data class SelectedBoard(
    val sourceId: String,
    val boardUrl: String,
    val boardName: String,
) {
    val key: String get() = selectedBoardKey(sourceId, boardUrl)
}

/** A source-level board request failure, retained so the UI can explain what can be retried. */
data class BoardLoadFailure(
    val source: Source,
    val cause: Throwable,
)

sealed interface BoardPickerUiState {
    data object Loading : BoardPickerUiState
    data object NoExtensions : BoardPickerUiState

    /** Includes successful empty sources; [failures] is non-empty when this is a partial failure. */
    data class Content(
        val sources: List<SourceWithBoards>,
        val failures: List<BoardLoadFailure> = emptyList(),
    ) : BoardPickerUiState

    data class AllSourcesFailed(
        val failures: List<BoardLoadFailure>,
    ) : BoardPickerUiState
}

internal data class SourceBoardLoadResult(
    val source: Source,
    val boards: List<Board> = emptyList(),
    val failure: BoardLoadFailure? = null,
)

/** Stable identity used for selection across sources whose board URLs may overlap. */
fun selectedBoardKey(sourceId: String, boardUrl: String): String = "$sourceId:$boardUrl"

/**
 * Applies the picker controls without changing source order or grouping.
 * A query matches board names case-insensitively; selected-only is evaluated by source ID plus URL.
 */
fun filterSourceWithBoards(
    sources: List<SourceWithBoards>,
    query: String,
    selectedKeys: Set<String>,
    selectedOnly: Boolean,
): List<SourceWithBoards> {
    val normalizedQuery = query.trim()
    return sources.mapNotNull { (source, boards) ->
        val filteredBoards = boards.filter { board ->
            val matchesQuery = normalizedQuery.isEmpty() ||
                board.name.contains(normalizedQuery, ignoreCase = true)
            val isSelected = selectedBoardKey(source.id, board.url) in selectedKeys
            matchesQuery && (!selectedOnly || isSelected)
        }
        filteredBoards.takeIf(List<Board>::isNotEmpty)?.let { SourceWithBoards(source, it) }
    }
}

/** Selected board counts by source ID, limited to boards currently available in [sources]. */
fun selectedBoardCountsBySource(
    sources: List<SourceWithBoards>,
    selectedKeys: Set<String>,
): Map<String, Int> = sources.associate { (source, boards) ->
    source.id to boards.count { selectedBoardKey(source.id, it.url) in selectedKeys }
}

internal fun boardPickerUiState(
    sources: List<Source>,
    results: List<SourceBoardLoadResult>,
): BoardPickerUiState {
    if (sources.isEmpty()) return BoardPickerUiState.NoExtensions

    val failures = results.mapNotNull(SourceBoardLoadResult::failure)
    if (failures.size == sources.size) return BoardPickerUiState.AllSourcesFailed(failures)

    return BoardPickerUiState.Content(
        sources = results.map { SourceWithBoards(it.source, it.boards) },
        failures = failures,
    )
}

@HiltViewModel
class BoardPickerViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
) : ViewModel() {
    private val _uiState = MutableStateFlow<BoardPickerUiState>(BoardPickerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            extensionLoader.sourcesFlow.collectLatest(::loadBoards)
        }
    }

    /** Reload every source after an all- or partial-source failure. */
    fun retry() {
        viewModelScope.launch { loadBoards(extensionLoader.sourcesFlow.value) }
    }

    private suspend fun loadBoards(sources: List<Source>) {
        if (sources.isEmpty()) {
            _uiState.value = BoardPickerUiState.NoExtensions
            return
        }

        _uiState.value = BoardPickerUiState.Loading
        val results = mutableListOf<SourceBoardLoadResult>()
        for (source in sources) {
            results += loadBoardsForSource(source)
        }
        val state = boardPickerUiState(sources, results)
        _uiState.value = state
    }

    private suspend fun loadBoardsForSource(source: Source): SourceBoardLoadResult = runCatching {
        source.getBoards().distinctBy(Board::url)
    }.fold(
        onSuccess = { boards -> SourceBoardLoadResult(source = source, boards = boards) },
        onFailure = { error ->
            SourceBoardLoadResult(
                source = source,
                failure = BoardLoadFailure(source = source, cause = error),
            )
        },
    )
}
