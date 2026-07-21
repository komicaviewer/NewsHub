package tw.kevinzhang.newshub.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.data.domain.BoardSubscriptionEntity

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

        assertEquals(listOf("gamer", "gamer"), filtered.map(BoardSubscriptionEntity::sourceId))
        assertEquals("gamer", resolveSelectedSourceId("gamer", setOf("gamer", "komica")))
    }

    @Test
    fun `stale selected source falls back to all sources`() {
        assertNull(resolveSelectedSourceId("removed-source", setOf("gamer", "komica")))
    }

    private fun subscriptions() = listOf(
        subscription(id = "1", sourceId = "gamer"),
        subscription(id = "2", sourceId = "komica"),
        subscription(id = "3", sourceId = "gamer"),
    )

    private fun subscription(id: String, sourceId: String) = BoardSubscriptionEntity(
        id = id,
        collectionId = "collection",
        sourceId = sourceId,
        boardUrl = "https://example.com/$id",
        boardName = id,
        sortOrder = id.toInt(),
    )
}
