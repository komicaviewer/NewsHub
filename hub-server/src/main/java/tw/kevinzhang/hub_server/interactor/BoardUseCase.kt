package tw.kevinzhang.hub_server.interactor

import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.hub_server.data.*
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.data.board.BoardRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardUseCase @Inject constructor(
    private val boardRepository: BoardRepository,
) {

    fun getAllBoards() =
        boardRepository.getAllBoards()

    fun getSubscribedBoards(subscriber: String) =
        boardRepository.getSubscribed(subscriber)

    suspend fun getBoard(url: String): Board {
        val boards = getAllBoards().first()
        for (board in boards) {
            when (board.host) {
                Host.KOMICA -> {
                    if (url.startsWith(board.url)) return board
                }
                Host.GAMER -> {
                    if (board.url.toHttpUrl().host == url.toHttpUrl().host) return board
                }
                else -> continue
            }
        }
        throw NotImplementedError("url $url can't find board")
    }

    suspend fun subscribe(board: Board, subscriber: String) {
        boardRepository.subscribe(board, subscriber)
    }

    suspend fun unsubscribe(board: Board, subscriber: String) {
        boardRepository.unsubscribe(board, subscriber)
    }
}