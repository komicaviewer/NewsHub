package tw.kevinzhang.komica_api.request

import okhttp3.HttpUrl

interface ThreadRequestBuilder: RequestBuilder {
    fun setUrl(url: HttpUrl): ThreadRequestBuilder
}