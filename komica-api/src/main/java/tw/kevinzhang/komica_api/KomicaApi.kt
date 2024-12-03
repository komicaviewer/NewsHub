package tw.kevinzhang.komica_api

import okhttp3.OkHttpClient
import okhttp3.Request
import tw.kevinzhang.komica_api.interactor.*
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.KPost
import tw.kevinzhang.komica_api.request.BoardRequestBuilder
import tw.kevinzhang.komica_api.request.ThreadRequestBuilder

class KomicaApi (
    private val client: OkHttpClient,
) {
    fun getBoardRequestBuilder(board: KBoard): BoardRequestBuilder {
        return GetRequestBuilder().forBoard(board)
    }

    fun getThreadRequestBuilder(board: KBoard): ThreadRequestBuilder {
        return GetRequestBuilder().forThread(board)
    }

    suspend fun getAllBoard() =
        GetAllBoard().invoke()

    /**
     * 通常用於取得貼文底下的所有回覆貼文
     */
    suspend fun getAllPost(req: Request): List<KPost> {
        val urlParser = GetUrlParser().invoke(req.url.toKBoard())
        return if (urlParser.hasPostId(req.url)) {
            GetAllPost(client).invoke(req)
        } else {
            GetAllNews(client).invoke(req)
        }
    }
}