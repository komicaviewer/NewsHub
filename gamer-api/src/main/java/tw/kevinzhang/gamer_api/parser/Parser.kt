package tw.kevinzhang.gamer_api.parser

import okhttp3.Request
import okhttp3.ResponseBody

interface Parser<T> {
    fun parse(body: ResponseBody, req: Request): T
}