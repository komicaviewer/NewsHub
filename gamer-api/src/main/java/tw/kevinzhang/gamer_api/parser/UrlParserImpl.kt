package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl

class UrlParserImpl: UrlParser {
    override fun parseBsn(url: HttpUrl): String? {
        return url.queryParameter("bsn")
    }

    override fun parseSna(url: HttpUrl): String? {
        return url.queryParameter("snA")
    }

    override fun parseSn(url: HttpUrl): String? {
        return url.queryParameter("sn")
    }

    override fun parsePage(url: HttpUrl): Int {
        return url.queryParameter("page")?.toInt() ?: 1
    }

    override fun hasBsn(url: HttpUrl): Boolean {
        return parseBsn(url) != null
    }

    override fun hasSna(url: HttpUrl): Boolean {
        return parseSna(url) != null
    }

    override fun hasSn(url: HttpUrl): Boolean {
        return parseSn(url) != null
    }

    override fun hasPage(url: HttpUrl): Boolean {
        return parsePage(url) != null
    }

    override fun parseThreadUrl(url: HttpUrl): String {
        if (
            !hasBsn(url) ||
            !hasSna(url)
        ) throw IllegalArgumentException()

        val builder = url.newBuilder()
        builder.removeAllQueryParameters("sn")
        builder.removeAllQueryParameters("page")
        return builder.build().toUrl().toString()
    }
}