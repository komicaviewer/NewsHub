package tw.kevinzhang.newshub.ui.boards

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class BoardsViewModelCancellationTest {
    @Test
    fun `revocation cancellation cannot publish stale boards`() = runTest {
        val source = object : Source {
            override val id = "test.source"
            override val name = "Test"
            override val language = "en"
            override val version = 1
            override val iconUrl: String? = null
            override val supportsCommentPagination = false
            override val alwaysUseRawImage = false
            override val needsLogin = false

            override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
                throw CancellationException("repository revoked")
            }

            override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = emptyList()

            override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
        }

        try {
            loadBoardsOrEmpty(source)
            fail("CancellationException must propagate to collectLatest")
        } catch (_: CancellationException) {
            // Expected: a revoked domain must cancel and discard the obsolete Source result.
        }
    }
}
