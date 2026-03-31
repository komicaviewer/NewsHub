package tw.kevinzhang.gamer_api.request

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class BoardRequestBuilderTest {

    @Test
    fun `Test setPage with url expect successful`() {
        val req = RequestBuilderImpl()
            .setUrl("https://forum.gamer.com.tw/B.php?bsn=60076".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals("https://forum.gamer.com.tw/B.php?bsn=60076&page=1".toHttpUrl(), req.url)
    }

    @Test
    fun `Test setPage with url with page_num expect successful`() {
        val req = RequestBuilderImpl()
            .setUrl("https://forum.gamer.com.tw/B.php?page=2&bsn=60076".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals("https://forum.gamer.com.tw/B.php?bsn=60076&page=1".toHttpUrl(), req.url)
    }
}