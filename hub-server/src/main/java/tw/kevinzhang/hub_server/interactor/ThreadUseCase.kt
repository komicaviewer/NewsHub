package tw.kevinzhang.hub_server.interactor

import tw.kevinzhang.hub_server.data.Host
import tw.kevinzhang.hub_server.data.post.Post
import tw.kevinzhang.hub_server.data.post.ThreadRepository
import tw.kevinzhang.hub_server.data.post.gamer.GamerPost
import tw.kevinzhang.hub_server.data.post.komica.KomicaPost
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadUseCase @Inject constructor(
    private val boardUseCase: BoardUseCase,
    private val komicaThreadRepository: ThreadRepository<KomicaPost>,
    private val gamerThreadRepository: ThreadRepository<GamerPost>,
) {
    suspend fun getPostThread(
        threadUrl: String,
        page: Int
    ): List<Post> {
        val board = boardUseCase.getBoard(threadUrl)
        return when (board.host) {
            Host.KOMICA -> komicaThreadRepository.getPostThread(threadUrl, page, board)
            Host.GAMER -> gamerThreadRepository.getPostThread(threadUrl, page, board)
            else -> throw NotImplementedError("ThreadRepository not implement")
        }
    }

    suspend fun getRePostThread(
        threadUrl: String,
        rePostId: String,
        page: Int
    ): List<Post> {
        val board = boardUseCase.getBoard(threadUrl)
        return when (board.host) {
            Host.KOMICA -> komicaThreadRepository.getRePostThread(threadUrl, rePostId, page)
            Host.GAMER -> gamerThreadRepository.getRePostThread(threadUrl, rePostId, page)
            else -> throw NotImplementedError("ThreadRepository not implement")
        }
    }

    suspend fun removeThread(threadUrl: String) {
        val board = boardUseCase.getBoard(threadUrl)
        when (board.host) {
            Host.KOMICA -> komicaThreadRepository.removePostThread(threadUrl)
            Host.GAMER -> gamerThreadRepository.removePostThread(threadUrl)
            else -> throw NotImplementedError("ThreadRepository not implement")
        }
    }
}