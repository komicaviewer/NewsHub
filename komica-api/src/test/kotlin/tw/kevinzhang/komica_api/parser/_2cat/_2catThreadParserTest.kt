package tw.kevinzhang.komica_api.parser._2cat

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class _2catThreadParserTest {

    @Test
    fun `Test _2catThreadParser expect successful`() {
        val builder = _2catRequestBuilder()
        val parser = _2catThreadParser(_2catPostParser(_2catUrlParser(), _2catPostHeadParser(_2catUrlParser())), _2catRequestBuilder())
        val thread = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/ThreadPage.html")).toResponseBody(),
            builder.setUrl("https://2cat.org/granblue/?res=963".toHttpUrl()).build(),
        )
        assertEquals(6, thread.size)
    }
}