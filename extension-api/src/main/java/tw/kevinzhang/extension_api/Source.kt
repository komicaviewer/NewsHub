package tw.kevinzhang.extension_api

import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

interface Source {
    val id: String
    val name: String
    val language: String
    val version: Int
    val iconUrl: String?

    suspend fun getBoards(): List<Board>
    suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary>
    suspend fun getThread(summary: ThreadSummary): Thread
}
