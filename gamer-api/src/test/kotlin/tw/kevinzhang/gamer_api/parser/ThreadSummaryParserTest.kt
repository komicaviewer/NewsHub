package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.gamer_api.loadFile
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl

internal class ThreadSummaryParserTest {

    @Test
    fun `Test NewsParser expect successful`() {
        val parser = ThreadSummaryParser()
        val news = parser.parse(
            loadFile("./src/test/html/ThreadSummaries.html")!!.toResponseBody(),
            RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076".toHttpUrl()).build(),
        )
        assertEquals(news.title, "【問題】吃滷味/麻辣燙只點麵、肉片、菜，然後不點火鍋料跟豆製食品的人在想什麼啊的人")
    }
}