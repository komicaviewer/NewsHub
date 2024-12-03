package tw.kevinzhang.gamer_api.interactor

import tw.kevinzhang.gamer_api.request.RequestBuilder
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl

class GetRequestBuilder {
    fun invoke(): RequestBuilder {
        return RequestBuilderImpl()
    }
}