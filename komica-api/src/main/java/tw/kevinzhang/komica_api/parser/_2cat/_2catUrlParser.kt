package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl
import tw.kevinzhang.komica_api.parser.UrlParser

class _2catUrlParser: UrlParser {
    override fun parseBoardId(url: HttpUrl): String? {
        return url.pathSegments[0]
    }

    override fun parsePostId(url: HttpUrl): String? {
        return parseRePostId(url) ?: parseHeadPostId(url)
    }

    override fun parseHeadPostId(url: HttpUrl): String? {
        return url.queryParameter("res")
    }

    override fun parseRePostId(url: HttpUrl): String? {
        return  url.fragment?.substring(1)
    }

    override fun parsePage(url: HttpUrl): Int? {
        return url.queryParameter("page")?.toInt()
    }

    override fun hasPage(url: HttpUrl): Boolean {
        return parsePage(url) != null
    }

    override fun hasPostId(url: HttpUrl): Boolean {
        return parsePostId(url) != null
    }

    override fun hasHeadPostId(url: HttpUrl): Boolean {
        return parseHeadPostId(url) != null
    }

    override fun hasRePostId(url: HttpUrl): Boolean {
        return parseRePostId(url) != null
    }

    override fun hasBoardId(url: HttpUrl): Boolean {
        return parseBoardId(url) != null
    }
}