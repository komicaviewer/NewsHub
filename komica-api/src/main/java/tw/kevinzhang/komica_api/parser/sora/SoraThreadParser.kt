package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.installThreadTag
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.filterRepliesBy
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestParser
import tw.kevinzhang.komica_api.toResponseBody

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
        setPreview(posts)
        return posts
    }

    private fun setRepliesCount(posts: List<KPost>) {
        for (post in posts) {
            post.replies = posts.filterRepliesBy(post.id).size
        }
    }

    private fun setPreview(posts: List<KPost>) {
        for (post in posts) {
            for (reply in post.content.filterIsInstance<KReplyTo>()) {
                val target = posts.find { it.id == reply.targetId }
                if (target != null) {
                    val paragraph = target.content.filterIsInstance<KText>()
                        .firstOrNull { it.content.isNotBlank() }
                    if (paragraph != null) {
                        reply.preview = "${paragraph.content.trim().take(10)}..."
                    }
                }
            }
        }
    }
}

