package tw.kevinzhang.newshub.interactor

import androidx.paging.*
import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.hub_server.data.post.Post
import tw.kevinzhang.hub_server.interactor.ThreadUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadInteractor @Inject constructor(
    private val threadUseCase: ThreadUseCase,
) {
    suspend fun removeThread(url: String) {
        threadUseCase.removeThread(url)
    }

    fun getAll(threadUrl: String, rePostId: String? = null): Flow<PagingData<Post>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { PostPagingSource(threadUseCase, threadUrl, rePostId) },
        ).flow
    }

    private class PostPagingSource(
        val threadUseCase: ThreadUseCase,
        val threadUrl: String,
        val rePostId: String? = null,
    ) : PagingSource<Int, Post>() {
        val isRePostThread = rePostId != null

        /**
         * page: 第一頁：1，第二頁：2，第三頁:3
         */
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Post> {
            val first = params.key == null
            val prev = if (first) null else params.key
            val next = if (first) 1 else params.key!! + 1
            val response = if (isRePostThread)
                threadUseCase.getRePostThread(threadUrl, rePostId!!, next)
            else
                threadUseCase.getPostThread(threadUrl, next)

            return if (response.isEmpty() || isRePostThread) {
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

        override fun getRefreshKey(state: PagingState<Int, Post>): Int? {
            return null
        }
    }
}