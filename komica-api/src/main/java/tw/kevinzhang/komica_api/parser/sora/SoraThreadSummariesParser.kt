package tw.kevinzhang.komica_api.parser.sora

import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.installThreadTag
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestParser
import tw.kevinzhang.komica_api.toResponseBody

class SoraThreadSummariesParser(
    private val postParser: Parser<KPost>,
    private val summariesReqParser: SoraThreadSummariesRequestParser,
    private val threadReqBuilder: SoraThreadRequestBuilder,
): Parser<List<KPost>> {
    override fun parse(res: ResponseBody, req: Request): List<KPost> {
        val source = Jsoup.parse(res.string())
        val summariesUrl = summariesReqParser.req(req).baseUrl()
        // get post secret name
//        String fsub = getElement().selectFirst("#fsub").attr("name");
//        String fcom = getElement().selectFirst("#fcom").attr("name");
        val threads = source.selectFirst("#threads").installThreadTag().select("div.thread")
        return threads.map { thread ->
            val threadpost = thread.selectFirst("div.threadpost")
            val postId = threadpost.attr("id").substring(1)
            val post = postParser.parse(
                threadpost.toResponseBody(),
                threadReqBuilder.setUrl(summariesUrl).setRes(postId).build(),
            )
            post.copy(replies = parseReplyCount(thread))
        }
    }

    companion object {
        fun parseReplyCount(thread: Element): Int {
            var replyCount = 0
            try {
                replyCount = thread.selectFirst("span.warn_txt2").text()
                    .replace("\\D".toRegex(), "")
                    .toInt()
            } catch (ignored: NullPointerException) { }
            replyCount += thread.getElementsByClass("reply").size
            return replyCount
        }
    }
}

