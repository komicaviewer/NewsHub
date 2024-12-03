package tw.kevinzhang.komica_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.parser._2cat._2catBoardParser
import tw.kevinzhang.komica_api.parser._2cat._2catPostHeadParser
import tw.kevinzhang.komica_api.parser._2cat._2catPostParser
import tw.kevinzhang.komica_api.parser._2cat._2catUrlParser
import tw.kevinzhang.komica_api.parser.sora.*
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraBoardRequestParser
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.toKBoard

class GetAllNews(
    private val client: OkHttpClient,
) {
    suspend fun invoke(req: Request): List<KPost> = withContext(Dispatchers.IO) {
        val board = req.url.toKBoard()
        val response = client.newCall(req).await()
        val urlParser = GetUrlParser().invoke(board)

        when (board) {
            is KBoard.Sora, KBoard.人外, KBoard.格鬥遊戲, KBoard.Idolmaster, KBoard.`3D-STG`, KBoard.魔物獵人, KBoard.`TYPE-MOON` ->
                SoraBoardParser(SoraPostParser(urlParser, SoraPostHeadParser()), SoraBoardRequestParser(), SoraThreadRequestBuilder())
            is KBoard._2catKomica ->
                SoraBoardParser(SoraPostParser(urlParser, _2catSoraPostHeadParser(SoraUrlParser())),  SoraBoardRequestParser(), SoraThreadRequestBuilder())
            is KBoard._2cat ->
                _2catBoardParser(_2catPostParser(urlParser, _2catPostHeadParser(_2catUrlParser())), _2catRequestBuilder())
            else ->
                throw NotImplementedError("BoardParser of $board not implemented yet")
        }.parse(response.body!!, req)
    }
}