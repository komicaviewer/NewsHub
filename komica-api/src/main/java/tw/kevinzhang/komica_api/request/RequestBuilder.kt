package tw.kevinzhang.komica_api.request

import okhttp3.Request

interface RequestBuilder {
    fun build(): Request
}