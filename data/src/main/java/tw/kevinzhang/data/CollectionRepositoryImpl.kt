package tw.kevinzhang.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.data.domain.CollectionDao
import tw.kevinzhang.data.domain.CollectionDatabase
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.data.domain.ParagraphListConverter
import tw.kevinzhang.data.domain.PostReadEntity
import tw.kevinzhang.data.domain.ReadingHistoryEntity
import tw.kevinzhang.data.domain.SavedPostEntity
import tw.kevinzhang.data.domain.SavedPostRecord
import tw.kevinzhang.data.domain.SourceIdentityDao
import tw.kevinzhang.data.domain.SourceIdentityEntity
import tw.kevinzhang.data.domain.SourceResolution
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.model.ThreadSummary
import java.util.UUID
import javax.inject.Inject

class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectionDao,
    private val db: CollectionDatabase,
    private val savedPostAssetStore: SavedPostAssetStore,
    private val sourceIdentityDao: SourceIdentityDao,
) : CollectionRepository, ReadingHistoryRepository, SavedPostRepository, SourceIdentityRepository {

    override fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeAll()

    override fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionRecord>> =
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
        sourceKey: String,
        boardUrl: String,
        boardName: String,
    ) {
        requireOnlineIdentity(sourceKey)
        if (dao.countSubscription(collectionId, sourceKey, boardUrl) > 0) return
        dao.insertSubscription(
            BoardSubscriptionEntity(
                id = UUID.randomUUID().toString(),
                collectionId = collectionId,
                sourceKey = sourceKey,
                boardUrl = boardUrl,
                boardName = boardName,
                sortOrder = 0,
            )
        )
    }

    override suspend fun removeBoardSubscription(subscriptionId: String) {
        dao.deleteSubscriptionById(subscriptionId)
    }

    override suspend fun removeAllSubscriptionsForSource(sourceKey: String) {
        dao.deleteSubscriptionsBySource(sourceKey)
    }

    override fun observeReadingHistory() = db.readingHistoryDao().observeAll()

    override fun observeReadPostIds(sourceKey: String, threadId: String): Flow<Set<String>> =
        db.postReadDao().observeReadPostIds(sourceKey, threadId).map { it.toSet() }

    override suspend fun recordRead(
        sourceKey: String,
        summary: ThreadSummary,
        sourceName: String?,
        boardName: String?,
    ) {
        val identity = requireOnlineIdentity(sourceKey)
        require(identity.sourceId == summary.sourceId) { "Thread source does not match canonical identity" }
        val converter = ParagraphListConverter()
        db.readingHistoryDao().upsert(
            ReadingHistoryEntity(
                sourceKey = sourceKey,
                sourceName = sourceName,
                threadId = summary.id,
                boardUrl = summary.boardUrl,
                boardName = boardName,
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

    override suspend fun markPostRead(sourceKey: String, threadId: String, postId: String) {
        requireOnlineIdentity(sourceKey)
        db.postReadDao().upsert(
            PostReadEntity(
                sourceKey = sourceKey,
                threadId = threadId,
                postId = postId,
                readAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun markPostsRead(sourceKey: String, threadId: String, postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        requireOnlineIdentity(sourceKey)
        val readAt = System.currentTimeMillis()
        db.postReadDao().upsertAll(
            postIds.distinct().map { postId ->
                PostReadEntity(
                    sourceKey = sourceKey,
                    threadId = threadId,
                    postId = postId,
                    readAt = readAt,
                )
            }
        )
    }

    override suspend fun clearHistory() {
        db.readingHistoryDao().deleteAll()
    }

    override fun observeSavedPosts(): Flow<List<SavedPostRecord>> = db.savedPostDao().observeAll()

    override fun observeSavedPost(sourceKey: String, threadId: String) =
        db.savedPostDao().observeById(sourceKey, threadId)

    override suspend fun savePost(entity: SavedPostEntity) {
        requireOnlineIdentity(entity.sourceKey)
        db.savedPostDao().upsert(entity)
    }

    override suspend fun unsavePost(sourceKey: String, threadId: String) {
        val entity = db.savedPostDao().getById(sourceKey, threadId)
        if (entity != null) {
            deleteScreenshots(entity)
            db.savedPostDao().delete(sourceKey, threadId)
        }
    }

    override suspend fun deleteAllSavedPosts() {
        val allPosts = db.savedPostDao().getAll()
        allPosts.forEach { deleteScreenshots(it) }
        db.savedPostDao().deleteAll()
    }

    private fun deleteScreenshots(entity: SavedPostEntity) {
        savedPostAssetStore.deleteSerializedReferences(entity.screenshotAssetRefs)
    }

    override suspend fun register(identity: SourceIdentity): SourceIdentityEntity {
        val canonical = CanonicalSourceIdentities.fromRuntimeIdentity(identity)
        sourceIdentityDao.insert(canonical)
        return requireNotNull(sourceIdentityDao.getByKey(canonical.sourceKey)).also { stored ->
            require(stored == canonical) { "Canonical source key is already bound to another identity" }
        }
    }

    override suspend fun getByKey(sourceKey: String): SourceIdentityEntity? =
        sourceIdentityDao.getByKey(sourceKey)

    override fun observeUnresolved(): Flow<List<SourceIdentityEntity>> =
        sourceIdentityDao.observeUnresolved()

    private suspend fun requireOnlineIdentity(sourceKey: String): SourceIdentityEntity {
        val identity = sourceIdentityDao.getByKey(sourceKey)
        require(identity?.resolution == SourceResolution.OFFICIAL) {
            "Unresolved source identity is offline-only"
        }
        return identity
    }
}
