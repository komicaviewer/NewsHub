package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl

interface UrlParser {
    fun parseBsn(url: HttpUrl): String?
    fun parseSna(url: HttpUrl): String?
    fun parseThreadUrl(url: HttpUrl): String
    fun parseSn(url: HttpUrl): String?
    fun parsePage(url: HttpUrl): Int
    fun hasBsn(url: HttpUrl): Boolean
    fun hasSna(url: HttpUrl): Boolean
    fun hasSn(url: HttpUrl): Boolean
    fun hasPage(url: HttpUrl): Boolean
}