package tw.kevinzhang.extensions_builtin.gamer

import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extensions_builtin.toExtParagraph
import tw.kevinzhang.gamer_api.GamerApi
import tw.kevinzhang.gamer_api.model.GImageInfo
import javax.inject.Inject

class GamerSource @Inject constructor(
    private val gamerApi: GamerApi,
) : Source {
    override val id = "tw.kevinzhang.gamer"
    override val name = "Gamer 巴哈姆特"
    override val language = "zh-TW"
    override val version = 1
    override val iconUrl: String? = null

    override suspend fun getBoards(): List<Board> =
        gamerApi.getAllBoard().map { gBoard ->
            Board(
                sourceId = id,
                url = gBoard.url,
                name = gBoard.name,
            )
        }

    override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> {
        val req = gamerApi.getRequestBuilder()
            .setUrl(board.url.toHttpUrl())
            .setPage(page.takeIf { it != 0 })
            .build()
        return gamerApi.getAllNews(req).map { gNews ->
            ThreadSummary(
                sourceId = id,
                boardUrl = board.url,
                id = gNews.url,
                title = gNews.title,
                author = gNews.posterName,
                createdAt = null, // GNews does not expose a creation timestamp
                replyCount = gNews.interactions,
                thumbnail = gNews.thumb,
                previewContent = listOf(
                    Paragraph.Text(gNews.preview)
                ),
            )
        }
    }

    override suspend fun getThread(summary: ThreadSummary): Thread {
        val req = gamerApi.getRequestBuilder()
            .setUrl(summary.id.toHttpUrl())
            .setPage(1)
            .build()
        val gPosts = gamerApi.getAllPost(req)
        return Thread(
            id = summary.id,
            url = getWebUrl(summary),
            title = summary.title,
            posts = gPosts.map { gPost ->
                val comments = if (gPost.commentsUrl.isNotBlank()) {
                    try {
                        val commentReq = gamerApi.getRequestBuilder()
                            .setUrl(gPost.commentsUrl.toHttpUrl())
                            .build()
                        gamerApi.getAllComment(commentReq).map { gComment ->
                            Comment(
                                id = gComment.sn,
                                author = gComment.nick,
                                createdAt = gComment.wtime.toLongOrNull()?.times(1000),
                                content = listOf(
                                    Paragraph.Text(gComment.content)
                                ),
                            )
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                Post(
                    id = gPost.id,
                    author = gPost.posterName,
                    createdAt = gPost.createdAt,
                    thumbnail = gPost.content.filterIsInstance<GImageInfo>().firstOrNull()?.thumb,
                    content = gPost.content.map { it.toExtParagraph() },
                    comments = comments,
                )
            },
        )
    }

    override fun getWebUrl(summary: ThreadSummary): String = summary.id
}
