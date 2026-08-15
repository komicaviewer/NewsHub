package tw.kevinzhang.newshub.ui.boards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_loader.RepositoryTrustDomainState

class BoardsScreenTest {
    @Test
    fun `group preview keeps original order and appends more after five boards`() {
        val boards = (1..7).map(::board)

        val items = buildBoardGroupItems(boards)

        assertEquals(6, items.size)
        assertEquals(
            boards.take(5),
            items.filterIsInstance<BoardGroupItem.BoardCard>().map { it.board },
        )
        assertTrue(items.last() is BoardGroupItem.More)
    }

    @Test
    fun `group with five or fewer boards does not show more`() {
        val items = buildBoardGroupItems((1..5).map(::board))

        assertEquals(5, items.size)
        assertFalse(items.any { it is BoardGroupItem.More })
    }

    @Test
    fun `empty state distinguishes quarantined extensions from no installed extensions`() {
        assertEquals("尚未安裝任何 Extension", boardsEmptyStateMessage(0))
        assertEquals(
            "已安裝的 Extension 無法通過安全驗證",
            boardsEmptyStateMessage(1),
        )
        assertEquals(
            "已安裝的 Extension 無法通過安全驗證",
            boardsEmptyStateMessage(13),
        )
    }

    @Test
    fun `empty state distinguishes repository trust failures`() {
        assertEquals(
            "Extension 來源中繼資料已過期",
            boardsEmptyStateMessage(1, listOf(RepositoryTrustDomainState.EXPIRED)),
        )
        assertEquals(
            "Extension 來源已暫停",
            boardsEmptyStateMessage(1, listOf(RepositoryTrustDomainState.SUSPENDED)),
        )
        assertEquals(
            "Extension 來源信任已撤銷",
            boardsEmptyStateMessage(
                1,
                listOf(RepositoryTrustDomainState.SUSPENDED, RepositoryTrustDomainState.REVOKED),
            ),
        )
    }

    @Test
    fun `all failed sources never become the no-extension state`() {
        val failedSource = SourceWithBoards(
            source = testSource(),
            boards = emptyList(),
            loadState = SourceBoardState.Failed(SourceFailure(SourceFailureCode.HOST_POLICY)),
        )

        assertFalse(shouldShowNoExtensions(isLoading = false, sources = listOf(failedSource)))
        assertTrue(shouldShowNoExtensions(isLoading = false, sources = emptyList()))
    }

    @Test
    fun `failure codes use fixed messages and authentication actions`() {
        SourceFailureCode.entries.forEach { code ->
            assertTrue(sourceBoardFailureMessage(code).isNotBlank())
        }
        assertEquals(
            SourceBoardFailureAction.LOGIN,
            sourceBoardFailureAction(SourceFailureCode.AUTH_REQUIRED, supportsLogin = true),
        )
        assertEquals(
            SourceBoardFailureAction.LOGIN,
            sourceBoardFailureAction(SourceFailureCode.AUTH_EXPIRED, supportsLogin = true),
        )
        assertEquals(
            SourceBoardFailureAction.RETRY,
            sourceBoardFailureAction(SourceFailureCode.AUTH_REQUIRED, supportsLogin = false),
        )
        assertEquals(
            SourceBoardFailureAction.RETRY,
            sourceBoardFailureAction(SourceFailureCode.HOST_POLICY, supportsLogin = true),
        )
    }

    @Test
    fun `zero count is shown only for a successful empty response`() {
        assertEquals(0, sourceBoardCountForDisplay(SourceBoardState.EmptySuccessfully))
        assertEquals(null, sourceBoardCountForDisplay(SourceBoardState.Failed(SourceFailure(SourceFailureCode.TIMED_OUT))))
        assertEquals(null, sourceBoardCountForDisplay(SourceBoardState.Loading))
        assertEquals(3, sourceBoardCountForDisplay(SourceBoardState.Ready(3)))
    }

    private fun testSource() = object : tw.kevinzhang.extension_api.Source {
        override val id = "failed.source"
        override val name = "Failed"
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoardPage(request: tw.kevinzhang.extension_api.model.BoardPageRequest) =
            tw.kevinzhang.extension_api.model.BoardPage(emptyList())
        override suspend fun getThreadSummaries(board: Board, page: Int) =
            emptyList<tw.kevinzhang.extension_api.model.ThreadSummary>()
        override suspend fun getThread(summary: tw.kevinzhang.extension_api.model.ThreadSummary) =
            error("not used")
    }

    private fun board(index: Int) = Board(
        sourceId = "source",
        url = "https://example.com/$index",
        name = "Board $index",
    )
}
