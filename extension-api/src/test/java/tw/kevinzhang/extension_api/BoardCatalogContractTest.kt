package tw.kevinzhang.extension_api

import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.extension_api.model.*

class BoardCatalogContractTest {
    @Test fun `wire rejects oversized pages even when Gson bypasses constructor`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionWireJson.decode<BoardPageRequest>("""{"pageSize":101}""")
        }
    }

    @Test fun `old and null requests receive safe defaults`() {
        listOf("{}", """{"query":null,"pageSize":null,"refresh":null}""").forEach {
            val request = ExtensionWireJson.decode<BoardPageRequest>(it)
            assertEquals(30, request.pageSize)
            assertEquals("", request.query.text)
            assertFalse(request.refresh)
        }
    }

    @Test fun `refresh cannot be combined with a continuation`() {
        assertThrows(IllegalArgumentException::class.java) {
            BoardPageRequest(pageToken = "next", refresh = true)
        }
    }

    @Test fun `legacy pages have unknown information and new pages round trip`() {
        assertNull(ExtensionWireJson.decode<BoardPage>("""{"boards":[]}""").catalogInfo)
        val page = BoardPage(emptyList(), catalogInfo = BoardCatalogInfo(
            BoardCatalogMode.CATALOG, 42L, true, BoardCatalogCoverage.PARTIAL,
        ))
        assertEquals(page, ExtensionWireJson.decode<BoardPage>(ExtensionWireJson.encode(page)))
        val unknown = ExtensionWireJson.decode<BoardPage>("""{"boards":[],"catalogInfo":{"mode":"future","coverage":"future"}}""")
        assertEquals(BoardCatalogCoverage.UNKNOWN, unknown.catalogInfo?.coverage)
        assertEquals(BoardCatalogMode.UNKNOWN, unknown.catalogInfo?.mode)
    }

    @Test fun `new request preserves its context without credentials`() {
        val request = BoardPageRequest(BoardQuery("遊戲", "pc"), pageSize = 1, refresh = true, contextEpoch = "generation-2")
        assertEquals(request, ExtensionWireJson.decode<BoardPageRequest>(ExtensionWireJson.encode(request)))
        assertEquals(100, BoardPageRequest(pageSize = 100).pageSize)
    }
}
