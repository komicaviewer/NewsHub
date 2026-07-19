package tw.kevinzhang.extension_api

import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class SourceContractTest {
    @Test fun `Source id must not be blank`() {
        val source = object : Source {
            override val id = "tw.test.source"
            override val name = "Test"
            override val language = "zh-TW"
            override val version = 1
            override val iconUrl = null
            override val supportsCommentPagination = false
            override val alwaysUseRawImage = false
            override val needsLogin = false
            override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())
            override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
            override suspend fun getThread(summary: ThreadSummary) = Thread("", null, null, emptyList())
        }
        assertTrue(source.id.isNotBlank())
    }
}
