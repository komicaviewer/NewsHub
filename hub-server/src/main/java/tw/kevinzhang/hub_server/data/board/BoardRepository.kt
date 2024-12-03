package tw.kevinzhang.hub_server.data.board

import kotlinx.coroutines.flow.Flow


interface BoardRepository {
    fun getAllBoards(): Flow<List<Board>>
    fun getSubscribed(subscriber: String): Flow<List<Board>>
    suspend fun subscribe(board: Board, subscriber: String)
    suspend fun unsubscribe(board: Board, subscriber: String)
    suspend fun clearAll()
}