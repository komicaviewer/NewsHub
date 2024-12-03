package tw.kevinzhang.komica_api.parser._2cat

import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup

class _2catThreadParser(
    private val postParser: Parser<KPost>,
    private val postRequestBuilder: _2catRequestBuilder,
): Parser<List<KPost>> {
    override fun parse(res: ResponseBody, req: Request): List<KPost> {
        val source = Jsoup.parse(res.string())
        val url = req.url
        return listOf(parseHead(source, url)).plus(parseReplies(source, url))
    }

    private fun parseHead(source: Element, url: HttpUrl): KPost {
        val req = postRequestBuilder.setUrl(url).build()
        return postParser.parse(
            source.selectFirst("div.threadpost").toResponseBody(),
            req,
        )
    }

    private fun parseReplies(source: Element, url: HttpUrl): List<KPost> {
        val posts = source.select("div[class=\"reply\"][id^='r']").map { reply_ele ->
            val replyId = reply_ele.id().substring(1) // r123456
            val req = postRequestBuilder.setUrl(url).setFragment("r$replyId").build()
            postParser.parse(reply_ele.toResponseBody(), req)
        }
        return posts
    }
}

