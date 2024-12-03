package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.parser.UrlParser
import tw.kevinzhang.komica_api.replaceChiWeekday
import tw.kevinzhang.komica_api.toMillTimestamp

class _2catSoraPostHeadParser(
    private val urlParser: UrlParser,
): PostHeadParser {
    override fun parseTitle(source: Element, url: HttpUrl): String? {
        val titleE = source.selectFirst("span.title")
        return titleE?.text()
    }

    override fun parseCreatedAt(source: Element, url: HttpUrl): Long {
        val detail = source.selectFirst("span.now") // text(): [22/09/24(土)15:36 ID:ZaUeAfRU/VsWt]
        return if (detail != null) {
            val post_detail = detail.text().split(" ID:")
            post_detail[0].trim().replaceChiWeekday().toMillTimestamp()
        } else {
            // is 2cat.komica.org
            0
        }
    }

    override fun parsePoster(source: Element, url: HttpUrl): String? {
        val id = urlParser.parsePostId(url)
        val detail = source.selectFirst("label[for=$id]") // text(): [22/09/24(土)15:36 ID:ZaUeAfRU/VsWt]
        return if (detail != null) {
            val post_detail = detail.text().split(" ID:")
            post_detail.getOrNull(1)?.trim()?.dropLast(1)
        } else {
            null
        }
    }
}