package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.parser.PostHeadParser
import tw.kevinzhang.komica_api.replaceChiWeekday
import tw.kevinzhang.komica_api.toMillTimestamp

class SoraPostHeadParser: PostHeadParser {
    override fun parseTitle(source: Element, url: HttpUrl): String? {
        val titleE = source.selectFirst("span.title")
        return titleE?.text()
    }

    override fun parseCreatedAt(source: Element, url: HttpUrl): Long {
        val detail = source.selectFirst("span.now") // text(): 2022/12/01(四) 00:32:32.704 ID:DO65QCZU
        return if (detail != null) {
            val post_detail = detail.text().split(" ID:")
            post_detail[0].trim().replaceChiWeekday().toMillTimestamp()
        } else {
            // is 2cat.komica.org
            0
        }
    }

    override fun parsePoster(source: Element, url: HttpUrl): String? {
        val detail = source.selectFirst("span.now") // text(): 2022/12/01(四) 00:32:32.704 ID:DO65QCZU
        return if (detail != null) {
            val post_detail = detail.text().split(" ID:")
            post_detail.getOrNull(1)?.trim()
        } else {
            null
        }
    }
}