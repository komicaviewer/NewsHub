package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl
import okhttp3.Request
import tw.kevinzhang.komica_api.removeFilename

class SoraThreadSummariesRequestParser {
    private lateinit var req: Request

    fun req(req: Request): SoraThreadSummariesRequestParser {
        this.req = req
        return this
    }

    fun baseUrl(): HttpUrl {
        return req.url.newBuilder()
            .removeFilename()
            .removeAllQueryParameters("page_num")
            .build()
    }
}