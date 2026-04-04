package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tw.kevinzhang.komica_api.addFilename
import tw.kevinzhang.komica_api.isZeroOrNull
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.removeFilename
import tw.kevinzhang.komica_api.request.ThreadSummariesRequestBuilder
import tw.kevinzhang.komica_api.setFilename
import tw.kevinzhang.komica_api.toKBoard

class SoraThreadSummariesRequestBuilder : ThreadSummariesRequestBuilder {
    private lateinit var builder: HttpUrl.Builder

    override fun setUrl(url: HttpUrl): SoraThreadSummariesRequestBuilder {
        this.builder = url.newBuilder()
        return this
    }

    fun setBoard(board: KBoard): SoraThreadSummariesRequestBuilder {
        setUrl(board.url.toHttpUrl())
        return this
    }

    // 只有 sora board 才有 page，sora thread 沒有
    override fun setPage(page: Int?): SoraThreadSummariesRequestBuilder {
        builder = builder
            .apply {
                if (page.isZeroOrNull()) {
                    removeFilename("htm")
                } else {
                    val _httpUrl = builder.build()
                    val extra = _httpUrl.pathSegments - _httpUrl.toKBoard().url.toHttpUrl().pathSegments
                    if (extra.isEmpty()) {
                        addFilename("$page", "htm")
                    } else {
                        setFilename("${page}.htm")
                    }
                }
            }
        return this
    }

    override fun build(): Request {
        return Request.Builder()
            .url(builder.build())
            .build()
    }
}