package tw.kevinzhang.newshub.interactor

import androidx.paging.*
import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.hub_server.data.comment.Comment
import tw.kevinzhang.hub_server.interactor.CommentUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentInteractor @Inject constructor(
    private val commentUseCase: CommentUseCase,
) {
    fun getAll(commentsUrl: String): Flow<PagingData<Comment>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false),
            pagingSourceFactory = { CommentPagingSource(commentUseCase, commentsUrl) },
        ).flow
    }

    private class CommentPagingSource(
        val commentUseCase: CommentUseCase,
        val commentsUrl: String,
    ) : PagingSource<Int, Comment>() {

        /**
         * page: 第一頁：1，第二頁：2，第三頁:3
         */
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Comment> {
            val first = params.key == null
            val prev = if (first) null else params.key
            val next = if (first) 1 else params.key!! + 1
            val response = commentUseCase.getAllComments(commentsUrl, next)

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

        override fun getRefreshKey(state: PagingState<Int, Comment>): Int? {
            return null
        }
    }
}