package tw.kevinzhang.gamer_api.request

import okhttp3.HttpUrl
import okhttp3.Request

class RequestBuilderImpl: RequestBuilder {
    private lateinit var builder: HttpUrl.Builder

    override fun setUrl(url: HttpUrl): RequestBuilder {
        this.builder = url.newBuilder()
        return this
    }

    override fun setBsn(bsn: String?): RequestBuilder {
        return if(bsn == null) removeReq("bsn")
        else addReq("bsn", bsn)
    }

    private fun addReq(key: String, bsn: String): RequestBuilder {
        if (hasReq(key))
            removeReq(key)
        builder = builder.addQueryParameter(key, bsn)
        return this
    }

    private fun removeReq(key: String): RequestBuilder {
        if(hasReq(key))
            builder = builder.removeAllQueryParameters(key)
        return this
    }

    private fun hasReq(key: String): Boolean {
        return builder.build().queryParameter(key).isNullOrBlank().not()
    }

    override fun setSna(sna: String?): RequestBuilder {
        return if(sna == null) removeReq("sna")
        else addReq("sna", sna)
    }

    override fun setSnb(snb: String?): RequestBuilder {
        return if(snb == null) removeReq("snb")
        else addReq("snb", snb)
    }

    override fun setPage(page: Int?): RequestBuilder {
        return if(page == null) removeReq("page")
        else addReq("page", page.toString())
    }

    override fun build(): Request {
        return Request.Builder()
            .url(builder.build())
            .build()
    }
}