package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.parser.UrlParser
import tw.kevinzhang.komica_api.replaceChiWeekday
import tw.kevinzhang.komica_api.toMillTimestamp

class _2catPostHeadParser(
    private val urlParser: UrlParser,
): PostHeadParser {
    override fun parseTitle(source: Element, url: HttpUrl): String? {
        val head = head(source, url)
        val titleEle = head.selectFirst("span.title")
        return titleEle?.text()
    }

    override fun parseCreatedAt(source: Element, url: HttpUrl): Long {
        val head = head(source, url).clone()
        head.selectFirst("span.title").remove()
        val s = head.text().trim()
        return if (s.isNotEmpty()) {
            val post_detail = s.substring(1, s.length - 1).split(" ID:")
//            println(post_detail[0])
            post_detail[0].trim()
                .replaceChiWeekday()
                .toMillTimestamp()
        } else {
            0L
        }
    }

    override fun parsePoster(source: Element, url: HttpUrl): String? {
        val head = head(source, url)

        // FIXME: wait for JavaScript render
        return null
    }

    private fun head(source: Element, url: HttpUrl): Element{
        return source.selectFirst("label[for=\"${urlParser.parsePostId(url)}\"]")
            ?: source.selectFirst("[fffwwbel]");
    }
}