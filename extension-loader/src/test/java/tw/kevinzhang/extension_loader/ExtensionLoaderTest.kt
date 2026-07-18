package tw.kevinzhang.extension_loader

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceRuntimeProvider
import tw.kevinzhang.extension_api.TwocatSourceIds
import tw.kevinzhang.extension_api.model.Board
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
        override suspend fun getBoards() = emptyList<Board>()
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread("", null, null, emptyList())
    }

    private fun loader(sources: List<Source>) = ExtensionLoaderImpl(
        okHttpClient = OkHttpClient(),
        runtimeProvider = SourceRuntimeProvider { error("Legacy test source must not request a runtime") },
        sources = sources,
    )

    @Test fun `getSource returns null for unknown id`() {
        val loader = loader(listOf(makeSource("tw.a")))
        assertNull(loader.getSource("tw.unknown"))
    }

    @Test fun `getSource returns built-in source by id`() {
        val source = makeSource("tw.a")
        val loader = loader(listOf(source))
        assertEquals(source, loader.getSource("tw.a"))
    }

    @Test fun `old-only twocat source remains usable without impersonating current`() {
        val legacy = makeSource(TwocatSourceIds.LEGACY)
        val loader = loader(listOf(legacy))

        assertEquals(listOf(legacy), loader.getAllSources())
        assertNull(loader.getSource(TwocatSourceIds.CURRENT))
        assertEquals(legacy, loader.getSource(TwocatSourceIds.HISTORICAL_LEGACY))
    }

    @Test fun `site2cat wins when both legacy aliases are installed`() {
        val historical = makeSource(TwocatSourceIds.HISTORICAL_LEGACY)
        val legacy = makeSource(TwocatSourceIds.LEGACY)
        val loader = loader(listOf(historical, legacy))

        assertEquals(listOf(legacy), loader.getAllSources())
        assertNull(loader.getSource(TwocatSourceIds.CURRENT))
    }

    @Test fun `current-only twocat source resolves legacy aliases`() {
        val current = makeSource(TwocatSourceIds.CURRENT)
        val loader = loader(listOf(current))

        assertEquals(listOf(current), loader.getAllSources())
        assertEquals(current, loader.getSource(TwocatSourceIds.LEGACY))
        assertEquals(current, loader.getSource(TwocatSourceIds.HISTORICAL_LEGACY))
    }

    @Test fun `current twocat source wins and deduplicates when both packages exist`() {
        val legacy = makeSource(TwocatSourceIds.LEGACY)
        val current = makeSource(TwocatSourceIds.CURRENT)
        val unrelated = makeSource("tw.other")
        val loader = loader(listOf(legacy, unrelated, current))

        assertEquals(listOf(current, unrelated), loader.getAllSources())
        assertEquals(current, loader.getSource(TwocatSourceIds.LEGACY))
    }

    @Test fun `sources without twocat are unchanged and aliases remain absent`() {
        val source = makeSource("tw.other")
        val loader = loader(listOf(source))

        assertEquals(listOf(source), loader.getAllSources())
        assertNull(loader.getSource(TwocatSourceIds.CURRENT))
        assertNull(loader.getSource(TwocatSourceIds.LEGACY))
    }
}
