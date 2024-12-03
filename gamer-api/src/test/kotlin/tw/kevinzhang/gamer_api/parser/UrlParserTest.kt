package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UrlParserTest {

    @Test
    fun `Test parseBsn expect successful`() {
        val parser = UrlParserImpl()
        val boardId = parser.parseBsn("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245".toHttpUrl())
        assertEquals("60076", boardId)
    }

    @Test
    fun `Test parseSna expect successful`() {
        val parser = UrlParserImpl()
        val threadId = parser.parseSna("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245".toHttpUrl())
        assertEquals("7398008", threadId)
    }

    @Test
    fun `Test parseSn expect successful`() {
        val parser = UrlParserImpl()
        val postId = parser.parseSn("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245".toHttpUrl())
        assertEquals("87956245", postId)
    }

    @Test
    fun `Test parsePage expect successful`() {
        val parser = UrlParserImpl()
        val page = parser.parsePage("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245&page=2".toHttpUrl())
        assertEquals(2, page)
    }

    @Test
    fun `Test hasSn expect successful`() {
        val parser = UrlParserImpl()
        val hasPostId = parser.hasSn("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasPage expect successful`() {
        val parser = UrlParserImpl()
        val hasPage = parser.hasPage("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245&page=2".toHttpUrl())
        assertEquals(true, hasPage)
    }

    @Test
    fun `Test parseThreadUrl expect successful`() {
        val parser = UrlParserImpl()
        val url = parser.parseThreadUrl("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008&sn=87956245&page=2".toHttpUrl())
        assertEquals("https://forum.gamer.com.tw/C.php?bsn=60076&snA=7398008", url)
    }
}