package tw.kevinzhang.newshub.ui.boards

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.*
import tw.kevinzhang.newshub.ui.collection.BoardCatalogCache

@OptIn(ExperimentalCoroutinesApi::class)
class BoardCatalogControllerTest {
    @Test fun `debounce and late responses do not publish obsolete queries`() = runTest {
        val requests = mutableListOf<String>()
        val source = source { request ->
            requests += request.query.text
            if (request.query.text == "a") withContext(NonCancellable) { delay(1000) }
            BoardPage(listOf(board(request.query.text)))
        }
        val controller = BoardCatalogController(source, BoardCatalogContext("id", "1"), BoardCatalogCache(), backgroundScope)
        controller.setQuery(BoardQuery("a"))
        advanceTimeBy(299); runCurrent()
        assertTrue(requests.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("a"), requests)
        controller.setQuery(BoardQuery("ab"))
        advanceTimeBy(300); runCurrent()
        assertEquals("ab", controller.state.value.boards.single().name)
        advanceTimeBy(1000); runCurrent()
        assertEquals("ab", controller.state.value.boards.single().name)
        controller.setQuery(BoardQuery(" ")); runCurrent()
        assertEquals("", requests.last())
        controller.close()
    }

    @Test fun `append error preserves data token and can retry without duplicate requests`() = runTest {
        var fail = true
        var calls = 0
        val controller = BoardCatalogController(source { request ->
            calls++
            if (request.pageToken == null) BoardPage(listOf(board("one")), "next")
            else {
                delay(10)
                if (fail) throw java.io.IOException()
                BoardPage(listOf(board("one"), board("two")))
            }
        }, BoardCatalogContext("id", "1"), BoardCatalogCache(), backgroundScope)
        controller.setQuery(BoardQuery()); runCurrent()
        controller.loadMore(); controller.loadMore(); advanceTimeBy(10); runCurrent()
        assertEquals(2, calls)
        assertEquals("next", controller.state.value.nextPageToken)
        assertNotNull(controller.state.value.moreFailure)
        fail = false
        controller.loadMore(); advanceTimeBy(10); runCurrent()
        assertEquals(listOf("one", "two"), controller.state.value.boards.map { it.name })
        assertNull(controller.state.value.nextPageToken)
        controller.close()
    }

    @Test fun `close discards in flight data and refresh is only a first page flag`() = runTest {
        val requests = mutableListOf<BoardPageRequest>()
        val controller = BoardCatalogController(source { request ->
            requests += request
            withContext(NonCancellable) { delay(20) }
            BoardPage(listOf(board("private")), "next")
        }, BoardCatalogContext("id", "epoch"), BoardCatalogCache(), backgroundScope)
        controller.refresh(); runCurrent()
        assertTrue(requests.single().refresh)
        assertEquals("epoch", requests.single().contextEpoch)
        controller.close(); advanceTimeBy(20); runCurrent()
        assertTrue(controller.state.value.boards.isEmpty())
    }

    private fun board(name: String) = Board("s", "https://test/$name", name, null)
    private fun source(load: suspend (BoardPageRequest) -> BoardPage) = object : Source {
        override val id = "s"
        override val name = "Test"
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoardPage(request: BoardPageRequest) = load(request)
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread("", null, null, emptyList())
    }
}
