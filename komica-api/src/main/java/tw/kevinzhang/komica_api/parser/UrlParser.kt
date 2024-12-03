package tw.kevinzhang.komica_api.parser

import okhttp3.HttpUrl

interface UrlParser {
    fun parseBoardId(url: HttpUrl): String?

    /**
     * returns head post of thread id or repost id
     */
    fun parsePostId(url: HttpUrl): String?
    fun parseHeadPostId(url: HttpUrl): String?
    fun parseRePostId(url: HttpUrl): String?
    fun parsePage(url: HttpUrl): Int?

    fun hasBoardId(url: HttpUrl): Boolean
    fun hasPostId(url: HttpUrl): Boolean
    fun hasHeadPostId(url: HttpUrl): Boolean
    fun hasRePostId(url: HttpUrl): Boolean
    fun hasPage(url: HttpUrl): Boolean
}