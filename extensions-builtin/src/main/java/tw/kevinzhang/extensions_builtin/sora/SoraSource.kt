package tw.kevinzhang.extensions_builtin.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.komica_api.KomicaApi
import tw.kevinzhang.komica_api.model.KBoard
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.boards
import javax.inject.Inject
import tw.kevinzhang.extension_api.model.Board as ExtBoard

class SoraSource @Inject constructor(
    private val api: KomicaApi,
) : Source {
    override val id = "tw.kevinzhang.komica-sora"
    override val name = "Sora Komica"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl: String? = null

    override suspend fun getBoards(): List<ExtBoard> =
        boards()
            .filterIsInstance<KBoard.Sora>()
            .map { kBoard -> ExtBoard(sourceId = id, url = kBoard.url, name = kBoard.name) }

    override suspend fun getThreadSummaries(board: ExtBoard, page: Int): List<ThreadSummary> {
        val kBoard = boards().first { it.url == board.url }
        val req = api.getBoardRequestBuilder(kBoard)
            .setPage(page)
            .build()
        return api.getAllPost(req).map { kPost ->
            ThreadSummary(
                sourceId = id,
                boardUrl = board.url,
                id = kPost.url,
                title = kPost.title,
                author = kPost.poster,
                createdAt = kPost.createdAt,
                replyCount = kPost.replies,
                thumbnail = kPost.content.filterIsInstance<KImageInfo>().firstOrNull()?.thumb,
                previewContent = kPost.content.map { it.toExtParagraph() },
            )
        }
    }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val kBoard = boards().first { it.url == summary.boardUrl }
        val req = api.getThreadRequestBuilder(kBoard)
            .setUrl(summary.id.toHttpUrl())
            .build()
        val posts = api.getAllPost(req)
        return Thread(
            id = summary.id,
            url = getWebUrl(summary),
            title = summary.title,
            posts = posts.map { kPost ->
                Post(
                    id = kPost.id,
                    author = kPost.poster,
                    createdAt = kPost.createdAt,
                    thumbnail = kPost.content.filterIsInstance<KImageInfo>().firstOrNull()?.thumb,
                    content = kPost.content.map { it.toExtParagraph() },
                    comments = emptyList(),
                )
            },
        )
    }

    override fun getWebUrl(summary: ThreadSummary): String = summary.id
}
