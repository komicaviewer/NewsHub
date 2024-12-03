package tw.kevinzhang.komica_api.parser.sora

import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.installThreadTag
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.filterReplyToIs
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestParser
import tw.kevinzhang.komica_api.toResponseBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup

class SoraThreadParser(
    private val postParser: Parser<KPost>,
    private val threadReqParser: SoraThreadRequestParser,
    private val threadReqBuilder: SoraThreadRequestBuilder,
): Parser<List<KPost>> {
    override fun parse(res: ResponseBody, req: Request): List<KPost> {
        val source = Jsoup.parse(res.string())
        val url = req.url
        return listOf(parseHeadPost(source, url)).plus(parseReplies(source, url))
    }

    private fun parseHeadPost(source: Element, url: HttpUrl): KPost {
        val req = threadReqBuilder.setUrl(url).build()
        return postParser.parse(source.selectFirst("div.threadpost").toResponseBody(), req)
    }

    private fun parseReplies(source: Element, url: HttpUrl): List<KPost> {
        val threads = source.selectFirst("#threads").installThreadTag().select("div.thread")
        val posts = threads.select("div.reply").map { reply_ele ->
            val fragment = reply_ele.attr("id") // r12345678
            val postId = fragment.substring(1)
            val req = threadReqBuilder.setUrl(url).setFragment("r$postId").build()
            postParser.parse(reply_ele.toResponseBody(), req)
        }
        setRepliesCount(posts)
        return posts
    }

    private fun setRepliesCount(posts: List<KPost>) {
        for (post in posts) {
            post.replies = posts.filterReplyToIs(post.id).size
        }
    }
}

