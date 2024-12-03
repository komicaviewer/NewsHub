package tw.kevinzhang.gamer_api.interactor

import tw.kevinzhang.gamer_api.parser.UrlParser
import tw.kevinzhang.gamer_api.parser.UrlParserImpl

class GetUrlParser {
    fun invoke(): UrlParser {
        return UrlParserImpl()
    }
}