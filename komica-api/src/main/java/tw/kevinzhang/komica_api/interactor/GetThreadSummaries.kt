package tw.kevinzhang.komica_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import tw.kevinzhang.komica_api.HttpException
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.parser._2cat._2catPostHeadParser
import tw.kevinzhang.komica_api.parser._2cat._2catPostParser
import tw.kevinzhang.komica_api.parser._2cat._2catThreadSummariesParser
import tw.kevinzhang.komica_api.parser._2cat._2catUrlParser
import tw.kevinzhang.komica_api.parser.sora.SoraPostHeadParser
import tw.kevinzhang.komica_api.parser.sora.SoraPostParser
import tw.kevinzhang.komica_api.parser.sora.SoraThreadSummariesParser
import tw.kevinzhang.komica_api.parser.sora.SoraUrlParser
import tw.kevinzhang.komica_api.parser.sora._2catSoraPostHeadParser
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import tw.kevinzhang.komica_api.request.komica2.Komica2ThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadRequestBuilder
import tw.kevinzhang.komica_api.request.sora.SoraThreadSummariesRequestParser
import tw.kevinzhang.komica_api.toKBoard

class GetThreadSummaries(
    private val client: OkHttpClient,
) {
    suspend fun invoke(req: Request): List<KPost> = withContext(Dispatchers.IO) {
        val board = req.url.toKBoard()
        val response = client.newCall(req).await()
        if (!response.isSuccessful) throw HttpException(response.code, req.url.toString())
        val urlParser = GetUrlParser().invoke(board)

        when (board) {
            is KBoard.Sora ->
                SoraThreadSummariesParser(
                    SoraPostParser(urlParser, SoraPostHeadParser()),
                    SoraThreadSummariesRequestParser(),
                    SoraThreadRequestBuilder()
                )

            is KBoard._2catSora ->
                SoraThreadSummariesParser(
                    SoraPostParser(
                        urlParser,
                        _2catSoraPostHeadParser(SoraUrlParser())
                    ), SoraThreadSummariesRequestParser(), SoraThreadRequestBuilder()
                )

            is KBoard._2cat ->
                _2catThreadSummariesParser(
                    _2catPostParser(
                        urlParser,
                        _2catPostHeadParser(_2catUrlParser())
                    ), _2catRequestBuilder()
                )

            is KBoard.Komica2 ->
                SoraThreadSummariesParser(
                    SoraPostParser(urlParser, SoraPostHeadParser()),
                    SoraThreadSummariesRequestParser(),
                    Komica2ThreadRequestBuilder()
                )
            else ->
                throw NotImplementedError("ThreadSummariesParser of $board not implemented yet")
        }.parse(response.body!!, req)
    }
}