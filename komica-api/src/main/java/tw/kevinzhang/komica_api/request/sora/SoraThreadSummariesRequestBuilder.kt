package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import tw.kevinzhang.komica_api.isZeroOrNull
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.request.ThreadSummariesRequestBuilder

open class SoraThreadSummariesRequestBuilder : ThreadSummariesRequestBuilder {
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
    // page 從 1 開始
    override fun setPage(page: Int?): SoraThreadSummariesRequestBuilder {
        builder = builder
            .apply {
                if (page.isZeroOrNull() || page == 1) {
                    removeAllQueryParameters("page_num")
                } else {
                    setQueryParameter("page_num", page.toString())
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