package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import tw.kevinzhang.komica_api.isImageUrl
import tw.kevinzhang.komica_api.isVideoUrl
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.KLink
import tw.kevinzhang.komica_api.model.KParagraph
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KPostBuilder
import tw.kevinzhang.komica_api.model.KQuote
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KVideoInfo
import tw.kevinzhang.komica_api.normalizeUrl
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.parser.UrlParser

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
        source.select("a").forEach { link ->
            val href = link.attr("href")
            val img = link.selectFirst("img.img")

            if (img != null && href.isNotEmpty()) {
                if (href.isImageUrl()) {
                    val thumbnailUrl = img.attr("src").ifEmpty {
                        img.attr("data-original")
                    }

                    if (thumbnailUrl.isNotEmpty()) {
                        builder.addContent(
                            KImageInfo(
                                thumbnailUrl.normalizeUrl(),
                                href.normalizeUrl(),
                            )
                        )
                    } else {
                        builder.addContent(KImageInfo(null, href.normalizeUrl()))
                    }
                } else if (href.isVideoUrl()) {
                    builder.addContent(
                        KVideoInfo(
                            href.normalizeUrl(),
                        )
                    )
                }
            }

        }
    }
}
