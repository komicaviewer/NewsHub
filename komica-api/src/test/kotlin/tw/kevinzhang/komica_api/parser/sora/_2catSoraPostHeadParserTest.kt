package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile

internal class _2catSoraPostHeadParserTest {

    @Test
    fun `Test parse title with 2cat HeadPost html expect successful`() {
        val parser = _2catSoraPostHeadParser(SoraUrlParser())
        val title = parser.parseTitle(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/HeadPost.html")),
            "https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()
        )
        assertEquals("巴突克戰舞131", title)
    }

    @Test
    fun `Test parse created at with 2cat HeadPost html expect successful`() {
        val parser = _2catSoraPostHeadParser(SoraUrlParser())
        val time = parser.parseCreatedAt(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/HeadPost.html")),
            "https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()
        )
        assertEquals(0, time)
    }

    @Test
    fun `Test parse poster at with 2cat HeadPost html expect successful`() {
        val parser = _2catSoraPostHeadParser(SoraUrlParser())
        val poster = parser.parsePoster(
            Jsoup.parse(loadFile("./src/test/html/org/komica/2cat/HeadPost.html")),
            "https://2cat.komica.org/~tedc21thc/new/pixmicat.php?res=4003068".toHttpUrl()
        )
        assertEquals("ZaUeAfRU/VsWt", poster)
    }
}