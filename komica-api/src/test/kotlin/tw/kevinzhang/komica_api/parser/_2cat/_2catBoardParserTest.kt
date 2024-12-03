package tw.kevinzhang.komica_api.parser._2cat

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraBoardRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class _2catBoardParserTest {

    @Test
    fun `Test _2catBoardParser expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = _2catBoardParser(_2catPostParser(_2catUrlParser(), _2catPostHeadParser(_2catUrlParser())), _2catRequestBuilder())
        val posts = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/BoardPage.html")).toResponseBody(),
            builder.setUrl("https://2cat.org/granblue".toHttpUrl()).build(),
        )
        assertEquals(8, posts.size)
    }
}