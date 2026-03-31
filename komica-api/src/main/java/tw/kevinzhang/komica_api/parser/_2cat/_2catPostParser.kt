package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.KLink
import tw.kevinzhang.komica_api.model.KParagraph
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KPostBuilder
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KVideoInfo
import tw.kevinzhang.komica_api.parser.Parser
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.parser.UrlParser
import java.util.regex.Pattern

class _2catPostParser(
    private val urlParser: UrlParser,
    private val postHeadParser: PostHeadParser,
): Parser<KPost> {
    private val builder = KPostBuilder()

    override fun parse(res: ResponseBody, req: Request): KPost {
        val source = Jsoup.parse(res.string())
        val httpUrl = req.url
        setDetail(source, httpUrl)
        setContent(source)
        setPicture(source, httpUrl)
        builder.setUrl(httpUrl.toString())
        builder.setPostId(urlParser.parsePostId(httpUrl)!!)
        return builder.build()
    }

    private fun setDetail(source: Element, url: HttpUrl) {
        builder.setTitle(postHeadParser.parseTitle(source, url) ?: "")
            .setPoster(postHeadParser.parsePoster(source, url) ?: "")
            .setCreatedAt(postHeadParser.parseCreatedAt(source, url) ?: 0L)
    }

    private fun setContent(source: Element) {
        val contents = source.selectFirst(".quote").childNodes()
            .filterIsInstance<TextNode>()
            .flatMap {
                resolveLink(it.text()) { link ->
                    if (link.match(IMAGE_URL_PATTERN)) {
                        KImageInfo(link, link)
                    } else if (link.match(VIDEO_URL_PATTERN)) {
                        KVideoInfo(link)
                    } else {
                        KLink(link)
                    }
                }
            }
        builder.setContent(contents)
    }

    private fun setPicture(source: Element, url: HttpUrl) {
        source.selectFirst("a.imglink[href=#]")?.let { thumbImg ->
            val fileName = source.selectFirst("a.imglink[href=#]").attr("title")
            val boardId = urlParser.parseBoardId(url);
            val newRawLink = "https://cat.2nyan.org/$boardId/src/${fileName}"
            val newThumbLink =
                "https://cat.2nyan.org/$boardId/thumb/${fileName.basename()}s.jpg"
            builder.addContent(
                KImageInfo(newThumbLink, newRawLink)
            )
        }
    }

    /**
     * 解析文章，裡面可能包含連結
     * @param article 文章
     */
    private fun resolveLink(article: String, callback: (String) -> KParagraph = {
        KLink(it)
    }): List<KParagraph> {
        val m = WEB_URL_PATTERN.matcher(article)
        val list: MutableList<KParagraph> = ArrayList()
        var index = 0
        while (m.find()) {
            val url = m.group()
            val preParagraph = article.substring(index, m.start())
            list.add(KText(preParagraph))
            list.add(callback(url))
            index = m.end()
        }
        val lastParagraph = article.substring(index)
        list.add(KText(lastParagraph))
        return list
    }

    private fun String.match(p: Pattern): Boolean {
        return p.matcher(this).find()
    }

    companion object {
        private val WEB_URL_PATTERN = Pattern.compile("((http?|https|ftp|file)://)?((W|w){3}.)?[a-zA-Z0-9]+\\.[a-zA-Z]+")
        private val IMAGE_URL_PATTERN = Pattern.compile("(http(s?):/)(/[^/]+)+\\.(?:jpg|gif|png)")
        private val VIDEO_URL_PATTERN = Pattern.compile("(http(s?):/)(/[^/]+)+\\.(?:webm|mp4)")
    }

    private fun String.basename(): String {
        val dotIndex = lastIndexOf('.')
        return if (dotIndex == -1) this else substring(0, dotIndex)
    }
}