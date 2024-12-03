package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl
import org.jsoup.nodes.Element

interface PostHeadParser {
    fun parseTitle(source: Element, url: HttpUrl): String?
    fun parseCreatedAt(source: Element, url: HttpUrl): Long?
    fun parsePoster(source: Element, url: HttpUrl): String?
}