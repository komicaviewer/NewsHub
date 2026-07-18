package tw.kevinzhang.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.CollectionDatabase
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.data.domain.ReadingHistoryEntity
import tw.kevinzhang.data.domain.SavedPostEntity

@RunWith(RobolectricTestRunner::class)
class CollectionRepositoryImplMigrationTest {
    private lateinit var db: CollectionDatabase
    private lateinit var repository: CollectionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CollectionDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = CollectionRepositoryImpl(db.collectionDao(), db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `moves legacy data, resolves collisions, and is idempotent`() = runBlocking {
        val current = "tw.kevinzhang.twocat"
        val legacy = "tw.kevinzhang.site2cat"
        val historical = "tw.kevinzhang.2cat"
        val dao = db.collectionDao()
        dao.insertCollection(CollectionEntity("collection", "Collection", 0))
        dao.insertSubscription(subscription("current-same", current, "same"))
        dao.insertSubscription(subscription("legacy-same", legacy, "same"))
        dao.insertSubscription(subscription("legacy-only", legacy, "legacy-only"))
        dao.insertSubscription(subscription("historical-only", historical, "historical-only"))

        db.readingHistoryDao().upsert(history(current, "collision", readAt = 20))
        db.readingHistoryDao().upsert(history(legacy, "collision", readAt = 10))
        db.readingHistoryDao().upsert(history(historical, "historical", readAt = 30))

        db.savedPostDao().upsert(saved(current, "collision", "[\"/current.png\"]"))
        db.savedPostDao().upsert(saved(legacy, "collision", "[\"/legacy.png\",\"/current.png\"]"))
        db.savedPostDao().upsert(saved(historical, "historical", "[\"/historical.png\"]"))

        repository.migrateSourceIds(linkedSetOf(legacy, historical), current)
        repository.migrateSourceIds(linkedSetOf(legacy, historical), current)

        assertEquals(
            setOf("same", "legacy-only", "historical-only"),
            dao.getSubscriptionsBySource(current).map { it.boardUrl }.toSet(),
        )
        assertEquals(emptyList<BoardSubscriptionEntity>(), dao.getSubscriptionsBySource(legacy))
        assertEquals(emptyList<BoardSubscriptionEntity>(), dao.getSubscriptionsBySource(historical))

        assertEquals(20L, db.readingHistoryDao().getById(current, "collision")?.readAt)
        assertEquals(30L, db.readingHistoryDao().getById(current, "historical")?.readAt)
        assertNull(db.readingHistoryDao().getById(legacy, "collision"))
        assertNull(db.readingHistoryDao().getById(historical, "historical"))

        assertEquals(
            "[\"/current.png\",\"/legacy.png\"]",
            db.savedPostDao().getById(current, "collision")?.screenshotPaths,
        )
        assertEquals(
            "[\"/historical.png\"]",
            db.savedPostDao().getById(current, "historical")?.screenshotPaths,
        )
        assertNull(db.savedPostDao().getById(legacy, "collision"))
        assertNull(db.savedPostDao().getById(historical, "historical"))
    }

    private fun subscription(id: String, sourceId: String, boardUrl: String) = BoardSubscriptionEntity(
        id = id,
        collectionId = "collection",
        sourceId = sourceId,
        boardUrl = boardUrl,
        boardName = boardUrl,
        sortOrder = 0,
    )

    private fun history(sourceId: String, threadId: String, readAt: Long) = ReadingHistoryEntity(
        sourceId = sourceId,
        threadId = threadId,
        boardUrl = "board",
        title = threadId,
        author = null,
        createdAt = null,
        commentCount = null,
        replyCount = null,
        thumbnail = null,
        rawImage = null,
        previewContent = "[]",
        sourceIconUrl = null,
        readAt = readAt,
    )

    private fun saved(sourceId: String, threadId: String, screenshotPaths: String) = SavedPostEntity(
        sourceId = sourceId,
        threadId = threadId,
        boardUrl = "board",
        title = threadId,
        author = null,
        createdAt = null,
        commentCount = null,
        replyCount = null,
        thumbnail = null,
        rawImage = null,
        previewContent = "[]",
        sourceIconUrl = null,
        threadUrl = null,
        savedAt = 1,
        screenshotPaths = screenshotPaths,
    )
}
