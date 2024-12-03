package tw.kevinzhang.gamer_api

import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element

/**
 * returns 49000 if the string is "49k"
 */
fun String.expandInt() =
    if (this.endsWith("k")) {
        this.substring(0, this.length - 1).toInt() * 1000
    } else {
        this.toInt()
    }

fun Element.toResponseBody(): ResponseBody {
    return this.toString().toResponseBody()
}