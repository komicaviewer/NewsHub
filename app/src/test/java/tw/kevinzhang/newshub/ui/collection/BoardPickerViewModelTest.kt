package tw.kevinzhang.newshub.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class BoardPickerViewModelTest {
    @Test
    fun filter_ignores_case_and_preserves_source_groups() {
        val sources = listOf(
            SourceWithBoards(source("komica"), listOf(board("komica", "a", "Anime"), board("komica", "b", "遊戲"))),
            SourceWithBoards(source("gamer"), listOf(board("gamer", "a", "ANIMATION"))),
        )

        val filtered = filterSourceWithBoards(sources, query = "ani", selectedKeys = emptySet(), selectedOnly = false)

        assertEquals(listOf("komica", "gamer"), filtered.map { it.source.id })
        assertEquals(listOf("Anime"), filtered[0].boards.map(Board::name))
        assertEquals(listOf("ANIMATION"), filtered[1].boards.map(Board::name))
    }

    @Test
    fun selected_only_filters_by_source_and_url_and_counts_each_group() {
        val sources = listOf(
            SourceWithBoards(source("komica"), listOf(board("komica", "same", "Komica"), board("komica", "other", "Other"))),
            SourceWithBoards(source("gamer"), listOf(board("gamer", "same", "Gamer"))),
        )
        val selected = setOf(selectedBoardKey("komica", "same"))

        val filtered = filterSourceWithBoards(sources, query = "", selectedKeys = selected, selectedOnly = true)

        assertEquals(listOf("komica"), filtered.map { it.source.id })
        assertEquals(listOf("Komica"), filtered.single().boards.map(Board::name))
        assertEquals(mapOf("komica" to 1, "gamer" to 0), selectedBoardCountsBySource(sources, selected))
    }

    @Test
    fun state_reports_no_extensions_content_partial_and_total_failure() {
        assertEquals(BoardPickerUiState.NoExtensions, boardPickerUiState(emptyList(), emptyList()))

        val successful = source("success")
        val failing = source("failing")
        val content = boardPickerUiState(
            listOf(successful, failing),
            listOf(
                SourceBoardLoadResult(successful, boards = listOf(board("success", "a", "A"))),
                SourceBoardLoadResult(failing, failure = BoardLoadFailure(failing, IllegalStateException("offline"))),
            ),
        ) as BoardPickerUiState.Content
        assertEquals(1, content.sources.single { it.source.id == "success" }.boards.size)
        assertEquals(listOf("failing"), content.failures.map { it.source.id })

        val allFailed = boardPickerUiState(
            listOf(failing),
            listOf(SourceBoardLoadResult(failing, failure = BoardLoadFailure(failing, IllegalStateException("offline")))),
        )
        assertTrue(allFailed is BoardPickerUiState.AllSourcesFailed)
    }

    private fun source(id: String): Source = object : Source {
        override val id = id
        override val name = id
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoards(): List<Board> = emptyList()
        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = emptyList()
        override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
    }

    private fun board(sourceId: String, url: String, name: String) = Board(sourceId, url, name)
}
