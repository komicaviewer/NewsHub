package tw.kevinzhang.newshub.ui.collection

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class MergedTimelinePagingSourceTest {
    @Test
    fun authenticationRequired_marks_source_for_a_foreground_notice() = runBlocking {
        val notifiedSourceIds = mutableListOf<String>()
        val source = object : Source {
            override val id = "gamer"
            override val sourceIdentity = identity(id)
            override val name = "Gamer"
            override val language = "zh-TW"
            override val version = 1
            override val iconUrl: String? = null
            override val supportsCommentPagination = false
            override val alwaysUseRawImage = false
            override val needsLogin = false

            override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())

            override suspend fun getThreadSummaries(
                board: Board,
                page: Int,
            ): List<ThreadSummary> = throw AuthenticationRequiredException()

            override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
        }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(
                subscription(
                    sourceId = source.id,
                    boardUrl = "https://forum.gamer.com.tw/B.php?bsn=60076",
                    boardName = "場外休息區",
                ),
            ),
            sourceResolver = { source },
            onAuthenticationRequired = notifiedSourceIds::add,
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        assertEquals(listOf(source.id), notifiedSourceIds)
        assertTrue(result is PagingSource.LoadResult.Error)
        val exception = (result as PagingSource.LoadResult.Error).throwable
        assertTrue(exception is MergedTimelineLoadException)
        assertTrue((exception as MergedTimelineLoadException).failures.single().cause is AuthenticationRequiredException)
    }

    @Test
    fun `a failed source does not hide successful board summaries`() = runBlocking {
        val failures = mutableListOf<SourceLoadFailure>()
        val healthy = source("healthy") { _, _ -> listOf(summary("healthy", "new", 20L)) }
        val unavailable = source("unavailable") { _, _ -> error("offline") }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(subscription("healthy"), subscription("unavailable")),
            sourceResolver = { id -> if (id == "healthy") healthy else unavailable },
            onAuthenticationRequired = {},
            onSourceLoadFailures = { newFailures -> failures.addAll(newFailures) },
        )

        val result = pagingSource.load(refreshParams())

        assertTrue(result is PagingSource.LoadResult.Page)
        assertEquals(listOf("new"), (result as PagingSource.LoadResult.Page).data.map { it.id })
        assertEquals(listOf("unavailable"), failures.map { it.sourceId })
    }

    @Test
    fun `all failed sources return one paging error with every failure`() = runBlocking {
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(subscription("first"), subscription("second")),
            sourceResolver = { id -> source(id) { _, _ -> error("$id offline") } },
            onAuthenticationRequired = {},
        )

        val result = pagingSource.load(refreshParams())

        assertTrue(result is PagingSource.LoadResult.Error)
        val exception = (result as PagingSource.LoadResult.Error).throwable
        assertTrue(exception is MergedTimelineLoadException)
        assertEquals(2, (exception as MergedTimelineLoadException).failures.size)
    }

    @Test
    fun `an empty successful source still keeps partial load successful`() = runBlocking {
        val healthy = source("healthy") { _, _ -> emptyList() }
        val unavailable = source("unavailable") { _, _ -> error("offline") }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(subscription("healthy"), subscription("unavailable")),
            sourceResolver = { id -> if (id == "healthy") healthy else unavailable },
            onAuthenticationRequired = {},
        )

        val result = pagingSource.load(refreshParams())

        assertTrue(result is PagingSource.LoadResult.Page)
        assertTrue((result as PagingSource.LoadResult.Page).data.isEmpty())
    }

    private fun refreshParams() =
        PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 20, placeholdersEnabled = false)

    private fun subscription(
        sourceId: String,
        boardUrl: String = "https://example.com/$sourceId",
        boardName: String = "$sourceId 看板",
    ): BoardSubscriptionRecord {
        val stored = CanonicalSourceIdentities.fromRuntimeIdentity(identity(sourceId))
        return BoardSubscriptionRecord(
            subscription = BoardSubscriptionEntity(
                id = "subscription-$sourceId",
                collectionId = "collection",
                sourceKey = stored.sourceKey,
                boardUrl = boardUrl,
                boardName = boardName,
                sortOrder = 0,
            ),
            sourceIdentity = stored,
        )
    }

    private fun summary(sourceId: String, id: String, createdAt: Long) = ThreadSummary(
        sourceId = sourceId,
        boardUrl = "https://example.com/$sourceId",
        id = id,
        title = id,
        author = null,
        createdAt = createdAt,
        commentCount = null,
        rawImage = null,
        thumbnail = null,
        previewContent = emptyList(),
    )

    private fun source(
        id: String,
        getSummaries: suspend (Board, Int) -> List<ThreadSummary>,
    ) = object : Source {
        override val id = id
        override val sourceIdentity = identity(id)
        override val name = id
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false

        override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())

        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> =
            getSummaries(board, page)

        override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
    }

    private fun identity(sourceId: String) = SourceIdentity(
        packageName = "test.$sourceId",
        signerSha256 = "a".repeat(64),
        sourceId = sourceId,
    )
}
