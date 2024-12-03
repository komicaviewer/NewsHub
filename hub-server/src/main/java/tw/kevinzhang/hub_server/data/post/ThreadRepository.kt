package tw.kevinzhang.hub_server.data.post

import tw.kevinzhang.hub_server.data.board.Board


interface ThreadRepository<T: Post> {
    suspend fun getPostThread(threadUrl: String, page: Int, board: Board): List<T>
    suspend fun getRePostThread(threadUrl: String, rePostId: String, page: Int): List<T>
    suspend fun removePostThread(threadUrl: String)
}