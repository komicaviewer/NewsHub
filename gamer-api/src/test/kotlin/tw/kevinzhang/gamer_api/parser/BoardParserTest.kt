package tw.kevinzhang.gamer_api.parser

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.gamer_api.loadFile
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class BoardParserTest {

    @Test
    fun `Test BoardParser expect successful`() {
        val parser = BoardParser(NewsParser(), RequestBuilderImpl())
        val newsList = parser.parse(
            loadFile("./src/test/html/BoardPage.html")!!.toResponseBody(),
            RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076".toHttpUrl()).build(),
        )
        assertEquals(23, newsList.size)
    }
}