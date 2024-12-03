package tw.kevinzhang.gamer_api.request

import okhttp3.HttpUrl
import okhttp3.Request

interface RequestBuilder {
    fun setUrl(url: HttpUrl): RequestBuilder
    fun setBsn(bsn: String?): RequestBuilder
    fun setSna(sna: String?): RequestBuilder
    fun setSnb(snb: String?): RequestBuilder
    fun setPage(page: Int?): RequestBuilder
    fun build(): Request
}