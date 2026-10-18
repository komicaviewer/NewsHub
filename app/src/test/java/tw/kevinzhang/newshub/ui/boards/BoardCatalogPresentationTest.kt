package tw.kevinzhang.newshub.ui.boards

import org.junit.Assert.*
import org.junit.Test
import tw.kevinzhang.extension_api.model.*

class BoardCatalogPresentationTest {
    @Test fun `unknown and partial catalogs never claim complete coverage`() {
        assertEquals("來源未提供目錄更新資訊", boardCatalogStatusText(null))
        assertTrue(boardCatalogStatusText(BoardCatalogInfo(coverage = BoardCatalogCoverage.PARTIAL)).contains("部分看板"))
        assertTrue(boardCatalogStatusText(BoardCatalogInfo(isStale = true)).contains("舊資料"))
    }
    @Test fun `empty query and empty search results use distinct descriptions`() {
        assertEquals("此來源目前沒有推薦看板", boardSearchEmptyText(""))
        assertEquals("找不到符合的看板", boardSearchEmptyText("手機"))
    }
}
