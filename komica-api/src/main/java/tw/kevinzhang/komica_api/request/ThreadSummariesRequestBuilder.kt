package tw.kevinzhang.komica_api.request

import okhttp3.HttpUrl

interface ThreadSummariesRequestBuilder : RequestBuilder {
    fun setUrl(url: HttpUrl): ThreadSummariesRequestBuilder
    fun setPage(page: Int?): ThreadSummariesRequestBuilder
}