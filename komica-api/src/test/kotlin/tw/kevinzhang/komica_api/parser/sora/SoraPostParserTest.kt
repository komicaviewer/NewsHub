package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.parser.komica2.Komica2PostHeadParser
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody

internal class SoraPostParserTest {

    @Test
    fun `Test parse post with 綜合 ReplyPost html expect successful`() {
        val builder = SoraThreadSummariesRequestBuilder()
        val parser = SoraPostParser(SoraUrlParser(), SoraPostHeadParser())
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/ReplyPost.html")).toResponseBody(),
            builder.setUrl( "https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl()).build(),
        )
        assertEquals("25208017", post.id)
    }

    @Test
    fun `Test parse post with 2cat ReplyPost html expect successful`() {
        val builder = SoraThreadSummariesRequestBuilder()
        val parser = SoraPostParser(SoraUrlParser(), SoraPostHeadParser())
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/ReplyPost.html")).toResponseBody(),
            builder.setUrl( "https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()).build(),
        )
        assertEquals("4003068", post.id)
    }

    @Test
    fun `Test parse post with Komica2 ReplyPost html expect successful`() {
        val builder = SoraThreadSummariesRequestBuilder()
        val parser = SoraPostParser(SoraUrlParser(), Komica2PostHeadParser())
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica2/ReplyPost.html"))
                .toResponseBody(),
            builder.setUrl("https://2cat.uk/~chatura/pixmicat.php?res=88534".toHttpUrl())
                .build(),
        )
        println("post ${post}")
        assertEquals("88534", post.id)
    }
}