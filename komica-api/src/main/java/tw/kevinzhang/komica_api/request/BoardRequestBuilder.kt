package tw.kevinzhang.komica_api.request

import okhttp3.HttpUrl

interface BoardRequestBuilder: RequestBuilder {
    fun setUrl(url: HttpUrl): BoardRequestBuilder
    fun setPage(page: Int?): BoardRequestBuilder
}