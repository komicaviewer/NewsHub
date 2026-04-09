package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody

internal class _2catThreadSummariesParserTest {

    @Test
    fun `Test _2catBoardParser expect successful`() {
        val builder = SoraThreadSummariesRequestBuilder()
        val parser = _2catThreadSummariesParser(
            _2catPostParser(
                _2catUrlParser(),
                _2catPostHeadParser(_2catUrlParser())
            ), _2catRequestBuilder()
        )
        val posts = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/BoardPage.html")).toResponseBody(),
            builder.setUrl("https://2cat.org/granblue".toHttpUrl()).build(),
        )
        assertEquals(8, posts.size)
    }
}