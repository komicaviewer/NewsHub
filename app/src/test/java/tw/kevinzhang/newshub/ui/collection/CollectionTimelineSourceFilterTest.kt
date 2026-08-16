package tw.kevinzhang.newshub.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.extension_api.SourceIdentity

class CollectionTimelineSourceFilterTest {

    @Test
    fun `all sources keeps every subscription`() {
        val subscriptions = subscriptions()

        assertEquals(subscriptions, filterSubscriptionsBySource(subscriptions, selectedSourceId = null))
        assertNull(resolveSelectedSourceId(null, setOf("gamer", "komica")))
    }

    @Test
    fun `selected source keeps only its subscriptions`() {
        val filtered = filterSubscriptionsBySource(subscriptions(), selectedSourceId = "gamer")

        assertEquals(listOf("gamer", "gamer"), filtered.map { it.sourceIdentity.sourceId })
        assertEquals("gamer", resolveSelectedSourceId("gamer", setOf("gamer", "komica")))
    }

    @Test
    fun `stale selected source falls back to all sources`() {
        assertNull(resolveSelectedSourceId("removed-source", setOf("gamer", "komica")))
    }

    @Test
    fun `effective selection is the filter applied to the timeline`() {
        val subscriptions = subscriptions()
        val selectedSourceId = resolveSelectedSourceId("gamer", setOf("gamer", "komica"))

        assertEquals(
            listOf("gamer", "gamer"),
            filterSubscriptionsBySource(subscriptions, selectedSourceId)
                .map { it.sourceIdentity.sourceId },
        )
    }

    @Test
    fun `source filter reset requires a changed selection`() {
        assertEquals(false, shouldResetTimelinePosition("gamer", "gamer"))
        assertEquals(false, shouldResetTimelinePosition(null, null))
        assertEquals(true, shouldResetTimelinePosition("gamer", "komica"))
        assertEquals(true, shouldResetTimelinePosition(null, "gamer"))
        assertEquals(true, shouldResetTimelinePosition("gamer", null))
    }

    private fun subscriptions() = listOf(
        subscription(id = "1", sourceId = "gamer"),
        subscription(id = "2", sourceId = "komica"),
        subscription(id = "3", sourceId = "gamer"),
    )

    private fun subscription(id: String, sourceId: String): BoardSubscriptionRecord {
        val identity = SourceIdentity("test.$sourceId", "a".repeat(64), sourceId)
        val stored = CanonicalSourceIdentities.fromRuntimeIdentity(identity)
        return BoardSubscriptionRecord(
            subscription = BoardSubscriptionEntity(
                id = id,
                collectionId = "collection",
                sourceKey = stored.sourceKey,
                boardUrl = "https://example.com/$id",
                boardName = id,
                sortOrder = id.toInt(),
            ),
            sourceIdentity = stored,
        )
    }
}
