package tw.kevinzhang.extensions_builtin._2cat

import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board as ExtBoard
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.hub_server.data.Host
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.news.komica.KomicaNewsRepositoryImpl
import tw.kevinzhang.hub_server.data.post.komica.KomicaThreadRepositoryImpl
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.boards
import javax.inject.Inject
import tw.kevinzhang.hub_server.data.board.Board as HubBoard

class _2catSource @Inject constructor(
    private val newsRepo: KomicaNewsRepositoryImpl,
    private val threadRepo: KomicaThreadRepositoryImpl,
) : Source {
    override val id = "tw.kevinzhang.komica-2cat"
    override val name = "2cat Komica"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl: String? = null

    override suspend fun getBoards(): List<ExtBoard> =
        boards()
            .filter { it is KBoard._2catKomica || it is KBoard._2cat }
            .map { kBoard -> ExtBoard(sourceId = id, url = kBoard.url, name = kBoard.name) }

    override suspend fun getThreadSummaries(board: ExtBoard, page: Int): List<ThreadSummary> {
        val hubBoard = HubBoard(url = board.url, name = board.name, host = Host.KOMICA)
        return newsRepo.getAllNews(hubBoard, page).map { news ->
            ThreadSummary(
                sourceId = id,
                boardUrl = board.url,
                id = news.threadUrl,
                title = news.title,
                author = news.poster,
                createdAt = news.createdAt,
                replyCount = news.replies,
                thumbnail = news.content.filterIsInstance<Paragraph.ImageInfo>().firstOrNull()?.thumb,
                previewContent = news.content.map { it.toExtParagraph() },
            )
        }
    }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val hubBoard = HubBoard(url = summary.boardUrl, name = "", host = Host.KOMICA)
        val posts = threadRepo.getPostThread(summary.id, 1, hubBoard)
        return Thread(
            id = summary.id,
            title = summary.title,
            posts = posts.map { post ->
                Post(
                    id = post.id,
                    author = post.poster,
                    createdAt = post.createdAt,
                    thumbnail = post.content.filterIsInstance<Paragraph.ImageInfo>().firstOrNull()?.thumb,
                    content = post.content.map { it.toExtParagraph() },
                    comments = emptyList(),
                )
            },
        )
    }
}
