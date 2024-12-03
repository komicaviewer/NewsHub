package tw.kevinzhang.komica_api.parser.sora

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile
import tw.kevinzhang.komica_api.request.sora.SoraBoardRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class SoraThreadParserTest {

    @Test
    fun `Test parse thread with 綜合 ThreadPage html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraThreadParser(SoraPostParser(SoraUrlParser(), SoraPostHeadParser()), SoraBoardRequestBuilder())
        val pair = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/ThreadPage.html")).toResponseBody(),
            builder.setUrl( "https://sora.komica.org/00/pixmicat.php?res=25214959".toHttpUrl()).build(),
        )
        assertEquals(49, pair.size)
    }

    @Test
    fun `Test parse thread with 2cat ThreadPage html expect successful`() {
        val builder = SoraBoardRequestBuilder()
        val parser = SoraThreadParser(SoraPostParser(SoraUrlParser(), SoraPostHeadParser()),  SoraBoardRequestBuilder())
        val pair = parser.parse(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/ThreadPage.html")).toResponseBody(),
            builder.setUrl("https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()).build(),
        )
        assertEquals(37, pair.size)
    }
}