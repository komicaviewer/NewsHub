package tw.kevinzhang.komica_api.interactor

import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.parser.UrlParser
import tw.kevinzhang.komica_api.parser._2cat._2catUrlParser
import tw.kevinzhang.komica_api.parser.sora.SoraUrlParser

class GetUrlParser {
    fun invoke(board: KBoard): UrlParser {
        return when (board) {
            is KBoard.Sora ->
                SoraUrlParser()
            is KBoard._2catKomica ->
                SoraUrlParser()
            is KBoard._2cat ->
                _2catUrlParser()
            else ->
                throw NotImplementedError("BoardParser of $board not implemented yet")
        }
    }
}