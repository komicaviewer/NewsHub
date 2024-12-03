package tw.kevinzhang.hub_server.interactor

import tw.kevinzhang.hub_server.data.Host
import tw.kevinzhang.hub_server.data.comment.Comment
import tw.kevinzhang.hub_server.data.comment.CommentRepository
import tw.kevinzhang.hub_server.data.comment.gamer.GamerComment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentUseCase @Inject constructor(
    private val boardUseCase: BoardUseCase,
    private val gamerThreadRepository: CommentRepository<GamerComment>,
) {
    suspend fun getAllComments(
        commentsUrl: String,
        page: Int
    ): List<Comment> {
        val board = boardUseCase.getBoard(commentsUrl)
        return when (board.host) {
            Host.GAMER -> gamerThreadRepository.getAllComments(commentsUrl, page, board)
            else -> throw NotImplementedError("CommentRepository not implement")
        }
    }
}