package tw.kevinzhang.hub_server.data.comment

import tw.kevinzhang.hub_server.data.board.Board


interface CommentRepository<T: Comment> {
    suspend fun getAllComments(commentsUrl: String, page: Int, board: Board): List<T>
}