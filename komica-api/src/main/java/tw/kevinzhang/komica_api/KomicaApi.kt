package tw.kevinzhang.komica_api

import okhttp3.OkHttpClient
import okhttp3.Request
import tw.kevinzhang.komica_api.interactor.GetAllBoards
import tw.kevinzhang.komica_api.interactor.GetRequestBuilder
import tw.kevinzhang.komica_api.interactor.GetThread
import tw.kevinzhang.komica_api.interactor.GetThreadSummaries
import tw.kevinzhang.komica_api.interactor.GetUrlParser
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder
import tw.kevinzhang.komica_api.request.ThreadSummariesRequestBuilder

class KomicaApi (
    private val client: OkHttpClient,
) {
    fun getThreadSummariesRequestBuilder(board: KBoard): ThreadSummariesRequestBuilder {
        return GetRequestBuilder().forBoard(board)
    }

    fun getThreadRequestBuilder(board: KBoard): ThreadRequestBuilder {
        return GetRequestBuilder().forThread(board)
    }

    suspend fun getAllBoards() =
        GetAllBoards().invoke()

    /**
     * 通常用於取得貼文底下的所有回覆貼文
     */
    suspend fun getThread(req: Request): List<KPost> {
        val urlParser = GetUrlParser().invoke(req.url.toKBoard())
        return if (urlParser.hasPostId(req.url)) {
            GetThread(client).invoke(req)
        } else {
            GetThreadSummaries(client).invoke(req)
        }
    }
}