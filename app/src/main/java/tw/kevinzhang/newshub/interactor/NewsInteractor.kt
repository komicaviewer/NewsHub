package tw.kevinzhang.newshub.interactor

import android.util.Log
import androidx.paging.*
import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.data.news.News
import tw.kevinzhang.hub_server.interactor.NewsUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsInteractor @Inject constructor(
    private val newsUseCase: NewsUseCase,
) {
    suspend fun clearAll(boards: Set<Board>) {
        newsUseCase.clearAllNews(boards)
    }

    fun getAll(boards: Set<Board>): Flow<PagingData<News>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { NewsPagingSource(newsUseCase, boards) },
        ).flow
    }

    private class NewsPagingSource(
        val newsUseCase: NewsUseCase,
        val boards: Set<Board>,
    ) : PagingSource<Int, News>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, News> {
            if (boards.isEmpty()) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null
                )
            }
            val first = params.key == null
            val prev = if (first) null else params.key
            val next = if (first) 1 else params.key!! + 1
            val boardsWithNextPage = if (first) {
                boards.toMap(1)
            } else {
                boards.toMap(params.key!! + 1)
            }
            Log.d("NewsPagingSource", "is first? ${first}, prev: ${prev}, next: ${next}, boardsWithNextPage: ${boardsWithNextPage}")

            val response = newsUseCase.getAllNews(boardsWithNextPage)
            return if (response.isEmpty()) {
                LoadResult.Page(
                    data = response,
                    prevKey = prev,
                    nextKey = null
                )
            } else {
                LoadResult.Page(
                    data = response,
                    prevKey = prev,
                    nextKey = next
                )
            }
        }

        override fun getRefreshKey(state: PagingState<Int, News>): Int? {
            return null
        }

        private fun <K, V> Set<K>.toMap(fill: V): Map<K, V> {
            val map = mutableMapOf<K, V>()
            this.forEach {
                map[it] = fill
            }
            return map
        }
    }
}