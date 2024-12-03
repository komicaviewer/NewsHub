package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile

internal class _2catPostHeadParserTest {

    @Test
    fun `Test parse title expect successful`() {
        val parser = _2catPostHeadParser(_2catUrlParser())
        val title = parser.parseTitle(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/ReplyPost.html")),
            "https://2cat.org/granblue/?res=963#r23587".toHttpUrl()
        )
        assertEquals("無標題", title)
    }

    @Test
    fun `Test parse created at expect successful`() {
        val parser = _2catPostHeadParser(_2catUrlParser())
        val time = parser.parseCreatedAt(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/ReplyPost.html")),
            "https://2cat.org/granblue/?res=963#r23587".toHttpUrl()
        )
        assertEquals(1638198600000L, time)
    }
}