package tw.kevinzhang.extension_api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    @Test fun `null page token bridges to legacy getThread`() = runBlocking {
        val expectedThread = Thread("thread-1", null, "Title", emptyList())
        val source = testSource { expectedThread }

        val page = source.getThreadPage(testSummary, null)

        assertEquals(expectedThread.posts, page.posts)
        assertEquals(null, page.nextPageToken)
        assertEquals(expectedThread.id, page.metadata?.id)
        assertEquals(expectedThread.url, page.metadata?.url)
        assertEquals(expectedThread.title, page.metadata?.title)
    }

    @Test fun `non-null page token is unsupported by legacy source`() = runBlocking {
        val source = testSource { Thread("thread-1", null, "Title", emptyList()) }

        try {
            source.getThreadPage(testSummary, "opaque-next-token")
        } catch (_: UnsupportedOperationException) {
            return@runBlocking
        }

        throw AssertionError("Expected a legacy source to reject a non-null page token")
    }

    @Test fun `getThreadPage is a JVM interface default method`() {
        val method = Source::class.java.methods.single { method ->
            method.name == "getThreadPage" && method.parameterTypes.size == 3
        }

        assertTrue(method.isDefault)
    }

    @Test fun `board website defaults to board URL inside the isolated Source`() = runBlocking {
        val source = testSource { Thread("thread-1", null, "Title", emptyList()) }
        val board = Board(
            sourceId = source.id,
            url = "https://example.test/board",
            name = "Board",
            description = null,
        )

        assertEquals(board.url, source.getBoardWebUrl(board))
    }

    @Test fun `getBoardWebUrl is a JVM interface default method`() {
        val method = Source::class.java.methods.single { method ->
            method.name == "getBoardWebUrl" && method.parameterTypes.size == 2
        }

        assertTrue(method.isDefault)
    }

    private fun testSource(getThread: suspend (ThreadSummary) -> Thread): Source = object : Source {
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
        override suspend fun getThread(summary: ThreadSummary) = getThread(summary)
    }

    private companion object {
        val testSummary = ThreadSummary(
            sourceId = "tw.test.source",
            boardUrl = "https://example.test/board",
            id = "thread-1",
            title = "Title",
            author = null,
            createdAt = null,
            commentCount = null,
            rawImage = null,
            thumbnail = null,
            previewContent = emptyList(),
        )
    }
}
