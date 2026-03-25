package tw.kevinzhang.extension_loader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

@RunWith(RobolectricTestRunner::class)
class ExtensionLoaderTest {

    private fun mockContext(): Context = ApplicationProvider.getApplicationContext()

    private fun makeSource(id: String) = object : Source {
        override val id = id
        override val name = id
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl = null
        override suspend fun getBoards() = emptyList<Board>()
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread("", null, emptyList())
    }

    @Test fun `getSource returns null for unknown id`() {
        val loader = ExtensionLoaderImpl(
            builtInSources = listOf(makeSource("tw.a")),
            context = mockContext(),
        )
        assertNull(loader.getSource("tw.unknown"))
    }

    @Test fun `getSource returns built-in source by id`() {
        val source = makeSource("tw.a")
        val loader = ExtensionLoaderImpl(builtInSources = listOf(source), context = mockContext())
        assertEquals(source, loader.getSource("tw.a"))
    }
}
