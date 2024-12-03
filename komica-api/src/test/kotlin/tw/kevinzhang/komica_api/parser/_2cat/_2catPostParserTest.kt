package tw.kevinzhang.komica_api.parser._2cat

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class _2catPostParserTest {

    @Test
    fun `Test _2catPostParser expect successful`() {
        val builder = _2catRequestBuilder()
        val parser = _2catPostParser(_2catUrlParser(), _2catPostHeadParser(_2catUrlParser()))
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/2cat/ReplyPost.html")).toResponseBody(),
            builder.setUrl("https://2cat.org/granblue/?res=963#r23587".toHttpUrl()).build(),
        )
        assertEquals("23587", post.id)
    }
}