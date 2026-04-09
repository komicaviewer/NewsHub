package tw.kevinzhang.komica_api

import okhttp3.HttpUrl
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import tw.kevinzhang.komica_api.model.boards
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 如果找不到thread標籤，就是2cat.komica.org，要用 [installThreadTag] 改成標準綜合版樣式
 */
fun Element.installThreadTag(): Element {
    if (this.selectFirst("div.thread") != null) return this

    //將thread加入threads中，變成標準綜合版樣式
    var thread = this.appendElement("div").addClass("thread")
    for (div in this.children()) {
        thread.appendChild(div)
        if (div.tagName() == "hr") {
            this.appendChild(thread)
            thread = this.appendElement("div").addClass("thread")
        }
    }
    return this
}

fun String.normalizeUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("http://") || startsWith("https://") -> this
        else -> this
    }
}

fun String.isImageUrl(): Boolean {
    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp")
    return imageExtensions.any { lowercase().contains(it) }
}

fun String.replaceJpnWeekday(): String {
    val dict = mapOf(
        "月" to "Mon",
        "火" to "Tue",
        "水" to "Wed",
        "木" to "Thu",
        "金" to "Fri",
        "土" to "Sat",
        "日" to "Sun",
    )
    var s = this
    for ((k, v) in dict) {
        s = s.replace(k, v)
    }
    return s
}

fun String.replaceChiWeekday(): String {
    val dict = mapOf(
        "一" to "Mon",
        "二" to "Tue",
        "三" to "Wed",
        "四" to "Thu",
        "五" to "Fri",
        "六" to "Sat",
        "日" to "Sun",
    )
    var s = this
    for ((k, v) in dict) {
        s = s.replace(k, v)
    }
    return s
}

fun HttpUrl.toKBoard() =
    boards().first { toString().contains(it.url) }

fun HttpUrl.Builder.setFilename(nameWithExtension: String?): HttpUrl.Builder {
    val lastIndex = build().pathSegments.lastIndex
    if (nameWithExtension != null) {
        setPathSegment(lastIndex, nameWithExtension)
    } else {
        removePathSegment(lastIndex)
    }
    return this
}

fun HttpUrl.Builder.addFilename(name: String, extension: String): HttpUrl.Builder {
    addPathSegment("$name.$extension")
    return this
}

fun HttpUrl.Builder.removeFilename(): HttpUrl.Builder {
    if (this.build().isFile()) {
        removePathSegment(build().pathSegments.lastIndex)
    }
    return this
}

fun HttpUrl.Builder.removeFilename(extension: String): HttpUrl.Builder {
    if (this.build().isFile(extension)) {
        removePathSegment(build().pathSegments.lastIndex)
    }
    return this
}

fun HttpUrl.isFile(extension: String): Boolean {
    if (!isFile()) return false
    val pathSegments = pathSegments
    val last = pathSegments.last()
    return last.endsWith(".$extension")
}

fun HttpUrl.isFile(): Boolean {
    val pathSegments = pathSegments
    val last = pathSegments.last()
    return last.contains(".")
}

fun Int?.isZeroOrNull() = this == 0 || this == null

fun Element.toResponseBody(): ResponseBody {
    return this.toString().toResponseBody()
}

fun String.toTimestamp(pattern: String, zoneId: String = "Asia/Taipei"): Long {
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val localDateTime = LocalDateTime.parse(this, formatter)
    return localDateTime.atZone(ZoneId.of(zoneId)).toInstant().toEpochMilli()
}