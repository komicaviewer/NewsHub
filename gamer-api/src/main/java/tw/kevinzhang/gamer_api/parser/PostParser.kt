package tw.kevinzhang.gamer_api.parser

import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import tw.kevinzhang.gamer_api.model.GImageInfo
import tw.kevinzhang.gamer_api.model.GLink
import tw.kevinzhang.gamer_api.model.GParagraph
import tw.kevinzhang.gamer_api.model.GPost
import tw.kevinzhang.gamer_api.model.GPostBuilder
import tw.kevinzhang.gamer_api.model.GText
import tw.kevinzhang.gamer_api.model.trim
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PostParser(
    private val urlParser: UrlParser,
): Parser<GPost> {
    private var builder = GPostBuilder()

    override fun parse(body: ResponseBody, req: Request): GPost {
        val source = Jsoup.parse(body.string())
        val postId = urlParser.parseSn(req.url)!!
        val bsn = urlParser.parseBsn(req.url)!!
        setTitle(source)
        setCreatedAt(source)
        setPosterName(source)
        setPosterId(source)
        setLike(source)
        setUnlike(source)
        setComments(source, postId)
        setCommentsUrl(bsn, postId)
        setContent(source)
        setRawHtml(source)
        builder.setUrl(req.url.toString())
        builder.setPostId(postId)
        builder.setPage(urlParser.parsePage(req.url))
        val post = builder.build()
        builder = GPostBuilder()
        return post
    }

    private fun setTitle(source: Element) {
        val text = source.selectFirst("div.c-post__header h1.c-post__header__title")?.text()
        if (text.isNullOrBlank().not())
            builder.setTitle(text!!)
    }

    private fun setCreatedAt(source: Element) {
        val element = source.selectFirst("div.c-post__header__info a.edittime.tippy-post-info")
        val string = element.attr("data-mtime")
        if (string.isNullOrBlank().not())
            builder.setCreatedAt(string.toTimestamp())
    }

    private fun setPosterName(source: Element) {
        val text = source.selectFirst("a.username").text()
        if (text.isNullOrBlank().not())
            builder.setPosterName(text)
    }

    private fun setPosterId(source: Element) {
        val text = source.selectFirst("a.userid").text()
        if (text.isNullOrBlank().not())
            builder.setPosterId(text)
    }

    private fun setContent(source: Element) {
        val list: MutableList<GParagraph> = ArrayList<GParagraph>()
        val parent = source.selectFirst("div.c-article__content")
        for (child in parent.childNodes().flatDiv()) {
            if (child is TextNode) {
                val content = child.text()
                if (content.trim().isEmpty()) {
                    continue
                }
                list.add(GText(content))
            }
            if (child is Element) {
                if (child.`is`("a.photoswipe-image")) {
                    val href = child.attr("href")
                    list.add(GImageInfo(href, href))
                } else if (child.`is`("a[href^=\"http://\"], a[href^=\"https://\"]")) {
                    list.add(GLink(child.ownText()))
                } else if (child.tagName() == "br") {
                    list.add(GText(""))
                }
            }
        }
        builder.setContent(list.trim())
    }

    private fun setRawHtml(source: Element) {
        val content = source.selectFirst("div.c-article__content") ?: return
        // Bahamut uses LazyLoad.js: server HTML has src="" and data-src="<real-url>".
        // JS fills src at runtime, but WebView runs with javaScriptEnabled=false,
        // so we normalize here before the fragment reaches the WebView.
        content.select("img[data-src]").forEach { img ->
            img.attr("src", img.attr("data-src"))
        }
        builder.setRawHtml(content.html())
    }

    private fun setLike(source: Element) {
        val string = source.selectFirst("div.gp a.count.tippy-gpbp-list").ownText()
        builder.setUnlike(string.toIntOrNull() ?: 0)
    }

    private fun setUnlike(source: Element) {
        val string = source.selectFirst("div.bp a.count.tippy-gpbp-list").ownText()
        builder.setUnlike(string.toIntOrNull() ?: 0)
    }

    private fun setComments(source: Element, postId: String) {
        val string = source.selectFirst("a#showoldCommend_$postId")?.text()?.filter { it.isDigit() }
        builder.setComments(string?.toIntOrNull() ?: 0)
    }

    private fun setCommentsUrl(bsn: String, postId: String) {
        builder.setCommentsUrl("https://forum.gamer.com.tw/ajax/moreCommend.php?bsn=$bsn&snB=$postId")
    }

    fun List<Node>.flatDiv(): List<Node> {
        return this.flatMap {
            if (it is Element && it.`is`("div")){
                it.childNodes().flatDiv()
            } else {
                listOf(it)
            }
        }
    }

    private fun String.toTimestamp(): Long {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
            formatter.parse(this).time
        } catch (ignored: ParseException) {
            0L
        }
    }
}