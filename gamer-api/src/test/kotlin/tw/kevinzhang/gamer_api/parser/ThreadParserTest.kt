package tw.kevinzhang.gamer_api.parser

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.gamer_api.loadFile
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class ThreadParserTest {

    @Test
    fun `Test ThreadParser expect successful`() {
        val parser = ThreadParser(PostParser(UrlParserImpl()), UrlParserImpl(), RequestBuilderImpl())
        val list = parser.parse(
            loadFile("./src/test/html/ThreadPage.html")!!.toResponseBody(),
            RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175".toHttpUrl()).build(),
        )
        assertEquals(
            listOf("夕立醬的潛水魚雷", "路過的雞蛋糕", "哈哈哈哈哈", "玫瑰捅你眼", "chi", "我是佑佑"),
            list.map { it.posterName }.subList(0, 6),
        )

        assertEquals(18, list.size)
    }
}