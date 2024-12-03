package tw.kevinzhang.hub_server.data.comment.gamer

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tw.kevinzhang.gamer_api.GamerApi
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.data.comment.CommentRepository
import tw.kevinzhang.newshub.di.IoDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject

class GamerCommentRepositoryImpl @Inject constructor(
    private val api: GamerApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
): CommentRepository<GamerComment> {

    override suspend fun getAllComments(
        commentsUrl: String,
        page: Int,
        board: Board
    ) = withContext(ioDispatcher) {
        try {
            val req = api.getRequestBuilder()
                .setUrl(commentsUrl.toHttpUrl())
                .build()
            val remote = api.getAllComment(req).map { it.toGamerComment(page) }
            remote
        } catch (e: Exception) {
            Log.e("GamerCommentRepo", e.stackTraceToString())
            emptyList()
        }
    }
}