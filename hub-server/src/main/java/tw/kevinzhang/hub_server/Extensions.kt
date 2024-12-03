package tw.kevinzhang.hub_server

fun String.trySubstring(range: IntRange): String {
    return if (length - 1 < (range.last - range.first)) {
        this
    } else {
        substring(range)
    }
}