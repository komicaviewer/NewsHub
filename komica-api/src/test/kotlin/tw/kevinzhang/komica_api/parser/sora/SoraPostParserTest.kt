package tw.kevinzhang.komica_api.parser.sora

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request.sora.SoraBoardRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class SoraPostParserTest {

    @Test
    fun `Test parse post with 綜合 ReplyPost html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraPostParser(SoraUrlParser(), SoraPostHeadParser())
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/ReplyPost.html")).toResponseBody(),
            builder.setUrl( "https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl()).build(),
        )
        assertEquals("25208017", post.id)
    }

    @Test
    fun `Test parse post with 2cat ReplyPost html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraPostParser(SoraUrlParser(), SoraPostHeadParser())
        val post = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/ReplyPost.html")).toResponseBody(),
            builder.setUrl( "https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()).build(),
        )
        assertEquals("4003068", post.id)
    }
}