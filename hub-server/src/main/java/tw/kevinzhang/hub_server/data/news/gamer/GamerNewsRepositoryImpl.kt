package tw.kevinzhang.hub_server.data.news.gamer

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tw.kevinzhang.gamer_api.GamerApi
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.data.news.NewsRepository
import tw.kevinzhang.newshub.di.IoDispatcher
import tw.kevinzhang.hub_server.di.TransactionProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

class GamerNewsRepositoryImpl @Inject constructor(
    private val dao: GamerNewsDao,
    private val api: GamerApi,
    private val transactionProvider: TransactionProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
): NewsRepository<GamerNews> {

    override suspend fun getAllNews(board: Board, page: Int): List<GamerNews> = withContext(ioDispatcher) {
        val news = dao.readAll(board.url, page)
        news.ifEmpty {
            try {
                val req = api.getRequestBuilder()
                    .setUrl(board.url.toHttpUrl())
                    .setPage(page.takeIf { it != 0 })
                    .build()
                val remote = api.getAllNews(req)
                    .map { it.toGamerPost(page, board.url) }
                transactionProvider.invoke {
                    dao.upsertAll(remote)
                }
                remote
            } catch (e: Exception) {
                Log.e("GamerPostRepo", e.stackTraceToString())
                emptyList()
            }
        }
    }

    override suspend fun clearAllNews() = withContext(ioDispatcher) {
        dao.clearAll()
    }
}