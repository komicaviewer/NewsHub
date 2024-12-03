package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.komica_api.loadFile

internal class SoraPostHeadParserTest {

    @Test
    fun `Test parse title with 綜合 HeadPost html expect successful`() {
        val parser = SoraPostHeadParser()
        val title = parser.parseTitle(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/HeadPost.html")),
            "https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl()
        )
        assertEquals("無題", title)
    }

    @Test
    fun `Test parse created at with 綜合 HeadPost html expect successful`() {
        val parser = SoraPostHeadParser()
        val time = parser.parseCreatedAt(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/HeadPost.html")),
            "https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl()
        )
        assertEquals(1638460800000L, time)
    }

    @Test
    fun `Test parse poster at with 綜合 HeadPost html expect successful`() {
        val parser = SoraPostHeadParser()
        val poster = parser.parsePoster(
            Jsoup.parse(loadFile("./src/test/html/org/komica/sora/HeadPost.html")),
            "https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl()
        )
        assertEquals("DO65QCZU", poster)
    }
}