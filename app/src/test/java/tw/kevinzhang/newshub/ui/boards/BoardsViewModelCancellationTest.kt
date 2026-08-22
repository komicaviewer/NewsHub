package tw.kevinzhang.newshub.ui.boards

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_api.SourceFailureException
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class BoardsViewModelCancellationTest {
    @Test
    fun `authenticated source does not issue board request before sign in`() = runTest {
        var requests = 0
        val source = authenticatedSource { requests++ }

        val result = loadBoards(source, AuthState.Expired)

        org.junit.Assert.assertEquals(SourceBoardState.LoginRequired, result.state)
        org.junit.Assert.assertEquals(0, requests)
    }

    @Test
    fun `authenticated source loads boards after sign in`() = runTest {
        var requests = 0
        val source = authenticatedSource { requests++ }

        val result = loadBoards(source, AuthState.SignedIn)

        org.junit.Assert.assertEquals(SourceBoardState.EmptySuccessfully, result.state)
        org.junit.Assert.assertEquals(1, requests)
    }

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
            loadBoards(source)
            fail("CancellationException must propagate to collectLatest")
        } catch (_: CancellationException) {
            // Expected: a revoked domain must cancel and discard the obsolete Source result.
        }
    }

    @Test
    fun `failure is retained as typed state instead of becoming a successful empty catalog`() = runTest {
        val source = sourceThrowing(IllegalStateException("secret URL https://example.test/private?token=secret"))

        val result = loadBoards(source)

        val failed = result.state as SourceBoardState.Failed
        org.junit.Assert.assertEquals(SourceFailureCode.EXTENSION_RUNTIME, failed.failure.code)
        org.junit.Assert.assertEquals("board_page", failed.failure.operation)
        org.junit.Assert.assertTrue(result.boards.isEmpty())
        org.junit.Assert.assertFalse(failed.failure.toString().contains("secret"))
    }

    private fun sourceThrowing(error: Throwable): Source = object : Source {
        override val id = "test.failure"
        override val name = "Failure"
        override val language = "en"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoardPage(request: BoardPageRequest): BoardPage = throw error
        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = emptyList()
        override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
    }

    private fun authenticatedSource(onBoardsRequested: () -> Unit): AuthenticatedSource =
        object : AuthenticatedSource {
            override val id = "test.oauth.signed-in"
            override val name = "OAuth"
            override val language = "en"
            override val version = 1
            override val iconUrl: String? = null
            override val supportsCommentPagination = false
            override val alwaysUseRawImage = false
            override val needsLogin = true
            override val authSpec = AuthSpec.OAuth("reddit", "reddit-installed", setOf("read"))

            override suspend fun validateSession(): Boolean = true
            override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
                onBoardsRequested()
                return BoardPage(emptyList())
            }
            override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = emptyList()
            override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
        }
}
