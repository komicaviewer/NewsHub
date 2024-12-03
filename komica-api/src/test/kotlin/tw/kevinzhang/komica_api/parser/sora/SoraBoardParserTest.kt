package tw.kevinzhang.komica_api.parser.sora

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request.sora.SoraBoardRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class SoraBoardParserTest {

    @Test
    fun `Test parse posts with 綜合 BoardPage html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraBoardParser(SoraPostParser(SoraUrlParser(), SoraPostHeadParser()), SoraBoardRequestBuilder())
        val posts = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/BoardPage.html")).toResponseBody(),
            builder.setUrl("https://sora.komica.org/00".toHttpUrl()).build(),
        )
        assertEquals(15, posts.size)
    }

    @Test
    fun `Test parse posts with 2cat BoardPage html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraBoardParser(SoraPostParser(SoraUrlParser(), SoraPostHeadParser()), SoraBoardRequestBuilder())
        val posts = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/BoardPage.html")).toResponseBody(),
            builder.setUrl("https://2cat.komica.org/~tedc21thc/new".toHttpUrl()).build(),
        )
        assertEquals(11, posts.size)
    }
}