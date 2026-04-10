package tw.kevinzhang.extensions_builtin.komica2

import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.komica_api.KomicaApi
import tw.kevinzhang.komica_api.model.KImageInfo
import tw.kevinzhang.komica_api.model.Komica2
import tw.kevinzhang.komica_api.request._2cat._2catRequestBuilder
import javax.inject.Inject
import tw.kevinzhang.extension_api.model.Board as ExtBoard

class Komica2Source @Inject constructor(
    private val api: KomicaApi,
) : Source {
    override val id = "tw.kevinzhang.komica2"
    override val name = "komica2"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl: String = "https://komica1.org/favicon.ico"
    override val supportsCommentPagination = false
    override val alwaysUseRawImage = true
    override val requiresLogin = false
    override val loginUrl = null
    override val loginPageLoadJs: String? = null

    override suspend fun getBoards(): List<ExtBoard> =
        Komica2()
            .map { kBoard -> ExtBoard(sourceId = id, url = kBoard.url, name = kBoard.name) }

    override suspend fun getThreadSummaries(board: ExtBoard, page: Int): List<ThreadSummary> {
        val kBoard = Komica2().first { it.url == board.url }
        val req = api.getThreadSummariesRequestBuilder(kBoard)
            .setPage(page)
            .build()
        return api.getThread(req).map { kPost ->
            val boardUrl =
                _2catRequestBuilder().setUrl(board.url.toHttpUrl()).setPage(null)
                    .build().url.toString()
            val postUrl =
                _2catRequestBuilder().setUrl(kPost.url.toHttpUrl()).setPage(null)
                    .build().url.toString()
            ThreadSummary(
                sourceId = id,
                boardUrl = boardUrl,
                id = postUrl,
                title = kPost.title,
                author = kPost.poster,
                createdAt = kPost.createdAt,
                commentCount = kPost.replies,
                thumbnail = kPost.content.filterIsInstance<KImageInfo>().firstOrNull()?.thumb,
                rawImage = kPost.content.filterIsInstance<KImageInfo>().firstOrNull()?.raw,
                previewContent = kPost.content.map { it.toExtParagraph() },
                replyCount = kPost.replies.takeIf { it > 0 },
            )
        }
    }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val kBoard = Komica2().first { it.url == summary.boardUrl }
        val req = api.getThreadRequestBuilder(kBoard)
            .setUrl(summary.id.toHttpUrl())
            .build()
        val posts = api.getThread(req)
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
                    replyCount = kPost.replies.takeIf { it > 0 },
                )
            },
        )
    }

    override fun getWebUrl(summary: ThreadSummary): String = summary.id
}
