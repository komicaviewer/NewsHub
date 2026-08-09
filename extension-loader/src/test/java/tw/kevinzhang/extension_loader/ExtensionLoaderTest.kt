package tw.kevinzhang.extension_loader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

@RunWith(RobolectricTestRunner::class)
class ExtensionLoaderTest {

    private fun makeSource(id: String) = object : Source {
        override val id = id
        override val name = id
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

    private fun loader(sources: List<Source>) = ExtensionLoaderImpl(sources)

    @Test fun `getSource returns null for unknown id`() {
        val loader = loader(listOf(makeSource("tw.a")))
        assertNull(loader.getSource("tw.unknown"))
    }

    @Test fun `getSource returns built-in source by id`() {
        val source = makeSource("tw.a")
        val loader = loader(listOf(source))
        assertEquals(source, loader.getSource("tw.a"))
    }

    @Test fun `duplicate ids are quarantined instead of selecting by order`() {
        val loader = loader(listOf(makeSource("tw.same"), makeSource("tw.same")))
        assertNull(loader.getSource("tw.same"))
    }

}
