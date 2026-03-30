package tw.kevinzhang.komica_api

class ParseException(override val message: String? = null): Exception(message)

class HttpException(val code: Int, val url: String) : Exception("HTTP $code: $url")