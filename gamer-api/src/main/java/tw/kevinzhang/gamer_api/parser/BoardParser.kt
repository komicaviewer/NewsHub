package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import tw.kevinzhang.gamer_api.model.GNews
import tw.kevinzhang.gamer_api.request.RequestBuilder
import tw.kevinzhang.gamer_api.toResponseBody
import java.util.logging.Logger

private val logger = Logger.getLogger("BoardParser")

class BoardParser(
    private val newsParser: NewsParser,
    private val requestBuilder: RequestBuilder,
): Parser<List<GNews>> {
    override fun parse(body: ResponseBody, req: Request): List<GNews> {
        val source = Jsoup.parse(body.string())
        val newsList = source.select("tr.b-list__row.b-list-item.b-imglist-item:not(.b-list__row--sticky)")
        if (newsList.isEmpty()) {
            logger.warning("parse: 0 items matched selector url=${req.url}")
        }
        return newsList.mapNotNull {
            try {
                val href = it.selectFirst("a[href^=\"C.php?bsn=\"]").attr("href")
                val threadUrl = req.url.newBuilder()
                    .replaceAfterHost("/$href")
                    .removeAllQueryParameters("tnum")
                    .build()
                newsParser.parse(it.toResponseBody(), requestBuilder.setUrl(threadUrl).build())
            } catch (e: Exception) {
                logger.warning("parse: skipping item — ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    private fun HttpUrl.Builder.replaceAfterHost(after: String): HttpUrl.Builder {
        require(after.startsWith("/")){ "after should be starts with /: $after" }
        val url = encodedPath("/").encodedQuery(null).encodedFragment(null)
        return (url.toString().substringBeforeLast("/") + after).toHttpUrl().newBuilder()
    }
}