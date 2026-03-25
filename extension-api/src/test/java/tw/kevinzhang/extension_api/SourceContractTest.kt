package tw.kevinzhang.extension_api

import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
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
            override suspend fun getBoards() = emptyList<Board>()
            override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
            override suspend fun getThread(summary: ThreadSummary) = Thread("", null, emptyList())
        }
        assertTrue(source.id.isNotBlank())
    }
}
