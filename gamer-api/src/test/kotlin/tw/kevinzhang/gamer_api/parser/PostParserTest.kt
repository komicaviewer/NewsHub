package tw.kevinzhang.gamer_api.parser

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.gamer_api.loadFile
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class PostParserTest {

    @Test
    fun `Test PostParser expect successful`() {
        val parser = PostParser(UrlParserImpl())
        val post = parser.parse(
            loadFile("./src/test/html/Post.html")!!.toResponseBody(),
            RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175&sn=46104650".toHttpUrl()).build(),
        )
        assertEquals("46104650", post.id)
    }
}