package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl
import okhttp3.Request
import tw.kevinzhang.komica_api.*

class SoraBoardRequestParser {
    private lateinit var req: Request

    fun req(req: Request): SoraBoardRequestParser {
        this.req = req
        return this
    }

    fun baseUrl(): HttpUrl {
        return req.url.newBuilder().removeFilename().build()
    }
}