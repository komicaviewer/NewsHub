package tw.kevinzhang.komica_api.request.komica2

import okhttp3.Request
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestBuilder

class Komica2ThreadSummariesRequestBuilder : SoraThreadSummariesRequestBuilder() {
    override fun build(): Request {
        val req = super.build()
        return if (req.url.host == "2cat.org") {
            req.newBuilder()
                .addHeader("Referer", "https://komica2.cc/mainmenu2022.html")
                .build()
        } else req
    }
}
