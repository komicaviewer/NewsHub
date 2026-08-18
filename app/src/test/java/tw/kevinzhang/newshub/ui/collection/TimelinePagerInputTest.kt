package tw.kevinzhang.newshub.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class TimelinePagerInputTest {

    @Test
    fun `source snapshot loading creates a new pager input that resolves the source`() {
        val subscriptions = listOf(subscription("ptt"))
        val source = source("ptt")
        val beforeSourcesLoad = createTimelinePagerInput(
            subscriptions = subscriptions,
            selectedSourceId = null,
            sources = emptyList(),
        )
        val afterSourcesLoad = createTimelinePagerInput(
            subscriptions = subscriptions,
            selectedSourceId = null,
            sources = listOf(source),
        )

        assertNotEquals(beforeSourcesLoad, afterSourcesLoad)
        assertNull(beforeSourcesLoad.resolveSource("ptt"))
        assertSame(source, afterSourcesLoad.resolveSource("ptt"))
    }

    @Test
    fun `pager input applies the selected source filter`() {
        val input = createTimelinePagerInput(
            subscriptions = listOf(subscription("ptt"), subscription("komica")),
            selectedSourceId = "komica",
            sources = listOf(source("ptt"), source("komica")),
        )

        assertEquals(listOf("komica"), input.subscriptions.map { it.sourceIdentity.sourceId })
        assertNull(input.resolveSource("missing"))
    }

    private fun subscription(sourceId: String): BoardSubscriptionRecord {
        val stored = CanonicalSourceIdentities.fromRuntimeIdentity(identity(sourceId))
        return BoardSubscriptionRecord(
            subscription = BoardSubscriptionEntity(
                id = "subscription-$sourceId",
                collectionId = "collection",
                sourceKey = stored.sourceKey,
                boardUrl = "https://example.com/$sourceId",
                boardName = sourceId,
                sortOrder = 0,
            ),
            sourceIdentity = stored,
        )
    }

    private fun source(sourceId: String) = object : Source {
        override val id = sourceId
        override val sourceIdentity = identity(sourceId)
        override val name = sourceId
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false

        override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())

        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> =
            emptyList()

        override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
    }

    private fun identity(sourceId: String) = SourceIdentity(
        packageName = "test.$sourceId",
        signerSha256 = "a".repeat(64),
        sourceId = sourceId,
    )
}
