package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import tw.kevinzhang.komica_api.ParseException
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.KLink
import tw.kevinzhang.komica_api.model.KParagraph
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KPostBuilder
import tw.kevinzhang.komica_api.model.KQuote
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KVideoInfo
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.parser.UrlParser
import tw.kevinzhang.komica_api.withHttps
import java.util.regex.Pattern

class SoraPostParser(
    private val urlParser: UrlParser,
    private val postHeadParser: PostHeadParser,
): Parser<KPost> {
    private val builder = KPostBuilder()

    override fun parse(res: ResponseBody, req: Request): KPost {
        val source = Jsoup.parse(res.string())
        val httpUrl = req.url
        setDetail(source, httpUrl)
        setContent(source)
        setPicture(source, httpUrl.host)
        builder.setUrl(httpUrl.toString())
        builder.setPostId(urlParser.parsePostId(httpUrl)!!)
        return builder.build()
    }

    private fun setDetail(source: Element, httpUrl: HttpUrl) {
        builder.setTitle(postHeadParser.parseTitle(source, httpUrl) ?: "")
            .setPoster(postHeadParser.parsePoster(source, httpUrl) ?: "")
            .setCreatedAt(postHeadParser.parseCreatedAt(source, httpUrl) ?: 0L)
    }

    private fun setContent(source: Element) {
        val list: MutableList<KParagraph> = ArrayList<KParagraph>()
        val parent = source.selectFirst(".quote")
        for (child in parent.childNodes()) {
            if (child is TextNode) {
                val content = child.text()
                if (content.trim().isEmpty()) {
                    continue
                }
                list.add(KText(content))
            }
            if (child is Element) {
                if (child.tagName() == "br") {
                    list.add(KText(""))
                }
                if (child.`is`("span.resquote")) {
                    val qlink = child.selectFirst("a.qlink")
                    if (qlink != null) {
                        val replyTo = qlink.text()
                            .replace(">".toRegex(), "") // for sora.komica.org
                            .replace("No.", "") // for 2cat.komica.org
                        list.add(KReplyTo(replyTo))
                    } else {
                        val quote = child.ownText().replace(">".toRegex(), "")
                        list.add(KQuote(quote))
                    }
                }
                if (child.`is`("a[href^=\"http://\"], a[href^=\"https://\"]")) {
                    list.add(KLink(child.ownText()))
                }
            }
        }
        builder.setContent(list)
    }

    private fun setPicture(source: Element, host: String) {
        source.selectFirst("img")?.let { thumbImg ->
            val rawUrl = thumbImg.parent().attr("href")
            val thumbUrl = thumbImg.attr("src")

            val (thumb, raw) = try {
                Pair(thumbUrl.withHttps(), rawUrl.withHttps())
            } catch (e: ParseException) {
                Pair(thumbUrl.withHttps(host), rawUrl.withHttps(host))
            }
            if (raw.match(IMAGE_URL_PATTERN)) {
                builder.addContent(KImageInfo(thumb, raw))
            } else if (raw.match(VIDEO_URL_PATTERN)) {
                builder.addContent(KVideoInfo(raw))
            } else { }
        }
    }

    private fun String.match(p: Pattern): Boolean {
        return p.matcher(this).find()
    }

    companion object {
        private val IMAGE_URL_PATTERN = Pattern.compile("(http(s?):/)(/[^/]+)+\\.(?:jpg|gif|png)")
        private val VIDEO_URL_PATTERN = Pattern.compile("(http(s?):/)(/[^/]+)+\\.(?:webm|mp4)")
    }
}
