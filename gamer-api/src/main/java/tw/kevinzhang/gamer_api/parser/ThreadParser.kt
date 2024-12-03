package tw.kevinzhang.gamer_api.parser

import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.kevinzhang.gamer_api.model.GPost
import tw.kevinzhang.gamer_api.request.RequestBuilder
import tw.kevinzhang.gamer_api.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class ThreadParser(
    private val postParser: Parser<GPost>,
    private val urlParser: UrlParser,
    private val requestBuilder: RequestBuilder,
): Parser<List<GPost>> {
    override fun parse(body: ResponseBody, req: Request): List<GPost> {
        println(req.url)
        val source = Jsoup.parse(body.string())
        val currentPage = currentPage(source)
        return listOf(parseHead(source, req, currentPage)).plus(parseReplies(source, req, currentPage))
    }

    private fun currentPage(source: Element): Int {
        val page = source.selectFirst("p.BH-pagebtnA a.pagenow")?.text()
        return page?.toInt() ?: 1
    }

    private fun parseHead(source: Element, req: Request, responsePage: Int): GPost {
        val section = source.selectFirst("section.c-section[id^=\"post_\"]")
        val postId = section.id().replace("post_", "")
        val postReq = requestBuilder
            .setUrl(req.url.toString().plus("&sn=$postId").toHttpUrl())
            .setPage(responsePage)
            .build()
        return postParser.parse(section.toResponseBody(), postReq)
    }

    private fun parseReplies(source: Element, req: Request, responsePage: Int): List<GPost> {
        val allPosts = source.select("section.c-section[id^=\"post_\"]")
        return allPosts.map { parseHead(it, req, responsePage) }.subList(1, allPosts.size)
    }
}

