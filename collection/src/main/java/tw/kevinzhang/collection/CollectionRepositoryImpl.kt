package tw.kevinzhang.collection

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.collection.data.CollectionDao
import tw.kevinzhang.collection.data.CollectionDatabase
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.collection.data.ParagraphListConverter
import tw.kevinzhang.collection.data.ReadingHistoryEntity
import tw.kevinzhang.collection.data.SavedPostEntity
import tw.kevinzhang.extension_api.model.ThreadSummary
import java.util.UUID
import javax.inject.Inject

class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectionDao,
    private val db: CollectionDatabase,
) : CollectionRepository, ReadingHistoryRepository, SavedPostRepository {

    override fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeAll()

    override fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>> =
        dao.observeSubscriptions(collectionId)

    override suspend fun createCollection(name: String, description: String, emoji: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertCollection(
            CollectionEntity(
                id = id,
                name = name,
                sortOrder = 0,
                description = description,
                emoji = emoji,
            )
        )
        return id
    }

    override suspend fun deleteCollection(id: String) {
        val entity = dao.getById(id) ?: return
        dao.deleteSubscriptionsByCollection(id)
        dao.deleteCollection(entity)
    }

    override suspend fun getCollectionById(id: String): CollectionEntity? = dao.getById(id)

    override suspend fun updateCollection(id: String, name: String, description: String, emoji: String) {
        val entity = dao.getById(id) ?: return
        dao.updateCollection(entity.copy(name = name, description = description, emoji = emoji))
    }

    override suspend fun reorderCollections(orderedIds: List<String>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val entity = dao.getById(id) ?: return@forEachIndexed
                dao.updateCollection(entity.copy(sortOrder = index))
            }
        }
    }

    override suspend fun addBoardSubscription(
        collectionId: String,
        sourceId: String,
        boardUrl: String,
        boardName: String,
    ) {
        if (dao.countSubscription(collectionId, sourceId, boardUrl) > 0) return
        dao.insertSubscription(
            BoardSubscriptionEntity(
                id = UUID.randomUUID().toString(),
                collectionId = collectionId,
                sourceId = sourceId,
                boardUrl = boardUrl,
                boardName = boardName,
                sortOrder = 0,
            )
        )
    }

    override suspend fun removeBoardSubscription(subscriptionId: String) {
        dao.deleteSubscriptionById(subscriptionId)
    }

    override suspend fun removeAllSubscriptionsForSource(sourceId: String) {
        dao.deleteSubscriptionsBySource(sourceId)
    }

    override fun observeReadingHistory() = db.readingHistoryDao().observeAll()

    override suspend fun recordRead(summary: ThreadSummary) {
        val converter = ParagraphListConverter()
        db.readingHistoryDao().upsert(
            ReadingHistoryEntity(
                sourceId = summary.sourceId,
                threadId = summary.id,
                boardUrl = summary.boardUrl,
                title = summary.title,
                author = summary.author,
                createdAt = summary.createdAt,
                commentCount = summary.commentCount,
                replyCount = summary.replyCount,
                thumbnail = summary.thumbnail,
                rawImage = summary.rawImage,
                previewContent = converter.toJson(summary.previewContent),
                sourceIconUrl = summary.sourceIconUrl,
                readAt = System.currentTimeMillis(),
            )
        )
    }

    override fun observeSavedPosts(): Flow<List<SavedPostEntity>> = db.savedPostDao().observeAll()

    override fun observeSavedPost(sourceId: String, threadId: String): Flow<SavedPostEntity?> =
        db.savedPostDao().observeById(sourceId, threadId)

    override suspend fun savePost(entity: SavedPostEntity) {
        db.savedPostDao().upsert(entity)
    }

    override suspend fun unsavePost(sourceId: String, threadId: String) {
        db.savedPostDao().delete(sourceId, threadId)
    }
}
