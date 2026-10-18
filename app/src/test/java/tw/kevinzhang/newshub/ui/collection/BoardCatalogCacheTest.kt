package tw.kevinzhang.newshub.ui.collection

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.newshub.ui.boards.BoardCatalogContext
import tw.kevinzhang.extension_api.model.*

class BoardCatalogCacheTest {
    @Test fun `cache does not cross identity epoch query or category`() = runTest {
        val cache = BoardCatalogCache()
        val context = BoardCatalogContext("a", "1")
        val query = BoardQuery("手機", "tech")
        val result = CachedBoardResult(listOf(Board("s", "https://test/1", "手機", null)), null, 42)
        cache.put(context, query, result)
        assertEquals(result, cache.get(context, query))
        assertNull(cache.get(context.copy(epoch = "2"), query))
        assertNull(cache.get(context.copy(identityKey = "b"), query))
        assertNull(cache.get(context, query.copy(categoryId = null)))
        cache.invalidate("a")
        assertNull(cache.get(context, query))
    }
}
