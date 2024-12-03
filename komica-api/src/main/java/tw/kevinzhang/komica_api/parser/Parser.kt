package tw.kevinzhang.komica_api.parser

import okhttp3.Request
import okhttp3.ResponseBody

interface Parser<T> {
    fun parse(res: ResponseBody, req: Request): T
}