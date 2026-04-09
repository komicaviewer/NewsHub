package tw.kevinzhang.komica_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import tw.kevinzhang.komica_api.HttpException
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.model.KReplyTo
import tw.kevinzhang.komica_api.model.replyTo
import tw.kevinzhang.komica_api.parser._2cat._2catPostHeadParser
import tw.kevinzhang.komica_api.parser._2cat._2catPostParser
import tw.kevinzhang.komica_api.parser._2cat._2catThreadParser
import tw.kevinzhang.komica_api.parser._2cat._2catUrlParser
import tw.kevinzhang.komica_api.parser.sora.SoraPostHeadParser
import tw.kevinzhang.komica_api.parser.sora.SoraPostParser
import tw.kevinzhang.komica_api.parser.sora.SoraThreadParser
import tw.kevinzhang.komica_api.parser.sora_komica2.SoraKomica2PostHeadParser
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestParser
import tw.kevinzhang.komica_api.request.sora_komica2.SoraKomica2ThreadRequestBuilder
import tw.kevinzhang.komica_api.toKBoard

class GetThread(
    private val client: OkHttpClient,
) {
    suspend fun invoke(req: Request): List<KPost> = withContext(Dispatchers.IO) {
        val response = client.newCall(req).await()
        if (!response.isSuccessful) throw HttpException(response.code, req.url.toString())
        val board = req.url.toKBoard()
        val urlParser = GetUrlParser().invoke(board)

        when (board) {
            is KBoard.Sora ->
                SoraThreadParser(
                    SoraPostParser(urlParser, SoraPostHeadParser()),
                    SoraThreadRequestParser(),
                    SoraThreadRequestBuilder()
                )
            is KBoard._2catSora ->
                SoraThreadParser(
                    SoraPostParser(urlParser, SoraPostHeadParser()),
                    SoraThreadRequestParser(),
                    SoraThreadRequestBuilder()
                )
            is KBoard._2cat ->
                _2catThreadParser(_2catPostParser(urlParser, _2catPostHeadParser(_2catUrlParser())), _2catRequestBuilder())
            is KBoard.SoraKomica2 ->
                SoraThreadParser(
                    SoraPostParser(urlParser, SoraKomica2PostHeadParser()),
                    SoraThreadRequestParser(),
                    SoraKomica2ThreadRequestBuilder()
                )
            else ->
                throw NotImplementedError("ThreadParser of ${req.url} not implemented yet")
        }.parse(response.body!!, req)
    }

    suspend fun withFillReplyTo(req: Request): List<KPost> = withContext(Dispatchers.IO) {
        val urlParser = GetUrlParser().invoke(req.url.toKBoard())
        val headPostId = urlParser.parseHeadPostId(req.url)!!
        val origin = invoke(req)
        origin.map { p ->
            if (p.replyTo().isEmpty()) {
                val originContent = p.content
                p.copy(content = listOf(KReplyTo(headPostId)).plus(originContent))
            } else {
                p
            }
        }
    }
}