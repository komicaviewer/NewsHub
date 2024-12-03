package tw.kevinzhang.newshub.interactor

import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.interactor.BoardUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardInteractor @Inject constructor(
    private val boardUseCase: BoardUseCase,
) {
    fun getAll() =
        boardUseCase.getAllBoards()

    fun getSubscribed(subscriber: String) =
        boardUseCase.getSubscribedBoards(subscriber)

    suspend fun get(url: String): Board {
        return boardUseCase.getBoard(url)
    }

    suspend fun subscribe(board: Board, subscriber: String) {
        boardUseCase.subscribe(board, subscriber)
    }

    suspend fun unsubscribe(board: Board, subscriber: String) {
        boardUseCase.unsubscribe(board, subscriber)
    }
}