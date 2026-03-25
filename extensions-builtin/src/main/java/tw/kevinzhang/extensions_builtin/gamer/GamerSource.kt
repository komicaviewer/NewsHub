package tw.kevinzhang.extensions_builtin.gamer

import kotlinx.coroutines.flow.first
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.hub_server.data.Host
import tw.kevinzhang.hub_server.data.board.BoardRepositoryImpl
import tw.kevinzhang.hub_server.data.news.gamer.GamerNewsRepositoryImpl
import tw.kevinzhang.hub_server.data.post.gamer.GamerThreadRepositoryImpl
import javax.inject.Inject
import tw.kevinzhang.hub_server.data.board.Board as HubBoard

class GamerSource @Inject constructor(
    private val boardRepo: BoardRepositoryImpl,
    private val newsRepo: GamerNewsRepositoryImpl,
    private val threadRepo: GamerThreadRepositoryImpl,
) : Source {
    override val id = "tw.kevinzhang.gamer"
    override val name = "Gamer 巴哈姆特"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl: String? = null

    override suspend fun getBoards(): List<Board> =
        boardRepo.getAllBoards().first()
            .filter { it.host == Host.GAMER }
            .map { board ->
                Board(
                    sourceId = id,
                    url = board.url,
                    name = board.name,
                )
            }

    override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> {
        val hubBoard = HubBoard(url = board.url, name = board.name, host = Host.GAMER)
        return newsRepo.getAllNews(hubBoard, page).map { news ->
            ThreadSummary(
                sourceId = id,
                boardUrl = board.url,
                id = news.threadUrl,
                title = news.title,
                author = news.posterName,
                createdAt = null,
                replyCount = news.interactions,
                thumbnail = null,
                previewContent = news.content.map { it.toExtParagraph() },
            )
        }
    }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val hubBoard = HubBoard(url = summary.boardUrl, name = "", host = Host.GAMER)
        val posts = threadRepo.getPostThread(summary.id, 1, hubBoard)
        return Thread(
            id = summary.id,
            title = summary.title,
            posts = posts.map { post ->
                Post(
                    id = post.id,
                    author = post.posterName,
                    createdAt = post.createdAt,
                    thumbnail = null,
                    content = post.content.map { it.toExtParagraph() },
                    comments = emptyList(),
                )
            },
        )
    }
}
