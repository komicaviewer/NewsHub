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
import tw.kevinzhang.extension_api.model.ThreadSummaryPage
import tw.kevinzhang.extension_api.model.ThreadSummaryPageRequest

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

    @Test
    fun `append forwards the opaque cursor returned by the first page`() = runBlocking {
        val requests = mutableListOf<ThreadSummaryPageRequest>()
        val source = cursorSource("reddit") { request ->
            requests += request
            when (request.pageToken) {
                null -> ThreadSummaryPage(
                    summaries = listOf(summary("reddit", "first", 20L)),
                    nextPageToken = "after=t3_first+a/b",
                )
                "after=t3_first+a/b" -> ThreadSummaryPage(
                    summaries = listOf(summary("reddit", "second", 10L)),
                    nextPageToken = null,
                )
                else -> error("Unexpected cursor: ${request.pageToken}")
            }
        }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(subscription("reddit")),
            sourceResolver = { source },
            onAuthenticationRequired = {},
        )

        val first = pagingSource.load(refreshParams()) as PagingSource.LoadResult.Page
        val second = pagingSource.load(appendParams(checkNotNull(first.nextKey))) as PagingSource.LoadResult.Page

        assertEquals(listOf(null, "after=t3_first+a/b"), requests.map { it.pageToken })
        assertEquals(listOf("first"), first.data.map { it.id })
        assertEquals(listOf("second"), second.data.map { it.id })
        assertEquals(null, second.nextKey)
    }

    @Test
    fun `append does not request the first page again for a board without a cursor`() = runBlocking {
        val requestsByBoard = mutableMapOf<String, MutableList<String?>>()
        val source = cursorSource("reddit") { request ->
            requestsByBoard.getOrPut(request.board.url) { mutableListOf() } += request.pageToken
            when (request.board.url.substringAfterLast('/')) {
                "exhausted" -> ThreadSummaryPage(
                    summaries = listOf(summary("reddit", "exhausted-first", 30L)),
                    nextPageToken = null,
                )
                "advancing" -> when (request.pageToken) {
                    null -> ThreadSummaryPage(
                        summaries = listOf(summary("reddit", "advancing-first", 20L)),
                        nextPageToken = "next-advancing",
                    )
                    "next-advancing" -> ThreadSummaryPage(
                        summaries = listOf(summary("reddit", "advancing-second", 10L)),
                        nextPageToken = null,
                    )
                    else -> error("Unexpected cursor: ${request.pageToken}")
                }
                else -> error("Unexpected board: ${request.board.url}")
            }
        }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(
                subscription("reddit", boardUrl = "https://example.com/exhausted"),
                subscription("reddit", boardUrl = "https://example.com/advancing"),
            ),
            sourceResolver = { source },
            onAuthenticationRequired = {},
        )

        val first = pagingSource.load(refreshParams()) as PagingSource.LoadResult.Page
        val second = pagingSource.load(appendParams(checkNotNull(first.nextKey))) as PagingSource.LoadResult.Page

        assertEquals(listOf<String?>(null), requestsByBoard.getValue("https://example.com/exhausted"))
        assertEquals(
            listOf(null, "next-advancing"),
            requestsByBoard.getValue("https://example.com/advancing"),
        )
        assertEquals(listOf("advancing-second"), second.data.map { it.id })
    }

    @Test
    fun `selected feed filters are passed unchanged to every board request`() = runBlocking {
        val requests = mutableListOf<ThreadSummaryPageRequest>()
        val source = cursorSource("reddit") { request ->
            requests += request
            ThreadSummaryPage(emptyList(), nextPageToken = null)
        }
        val selections = mapOf("sort" to "top", "time" to "week")
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(
                subscription("reddit", boardUrl = "https://example.com/r/android"),
                subscription("reddit", boardUrl = "https://example.com/r/kotlin"),
            ),
            sourceResolver = { source },
            onAuthenticationRequired = {},
            feedFiltersBySource = mapOf("reddit" to selections),
        )

        pagingSource.load(refreshParams())

        assertEquals(2, requests.size)
        assertTrue(requests.all { it.filters == selections })
    }

    private fun refreshParams() =
        PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 20, placeholdersEnabled = false)

    private fun appendParams(key: Int) =
        PagingSource.LoadParams.Append(key = key, loadSize = 20, placeholdersEnabled = false)

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

    private fun cursorSource(
        id: String,
        getPage: suspend (ThreadSummaryPageRequest) -> ThreadSummaryPage,
    ) = object : Source {
        override val id = id
        override val sourceIdentity = identity(id)
        override val name = id
        override val language = "en"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = true
        override val alwaysUseRawImage = false
        override val needsLogin = false

        override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())

        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> =
            error("Cursor source must use getThreadSummaryPage")

        override suspend fun getThreadSummaryPage(request: ThreadSummaryPageRequest): ThreadSummaryPage =
            getPage(request)

        override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
    }

    private fun identity(sourceId: String) = SourceIdentity(
        packageName = "test.$sourceId",
        signerSha256 = "a".repeat(64),
        sourceId = sourceId,
    )
}
