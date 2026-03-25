package tw.kevinzhang.collection

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.collection.data.CollectionDao
import tw.kevinzhang.collection.data.CollectionEntity
import java.util.UUID
import javax.inject.Inject

class CollectionRepositoryImpl @Inject constructor(
    private val dao: CollectionDao,
) : CollectionRepository {

    override fun observeCollections(): Flow<List<CollectionEntity>> = dao.observeAll()

    override fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>> =
        dao.observeSubscriptions(collectionId)

    override suspend fun createCollection(name: String) {
        dao.insertCollection(
            CollectionEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                sortOrder = 0,
            )
        )
    }

    override suspend fun deleteCollection(id: String) {
        val entity = dao.getById(id) ?: return
        dao.deleteCollection(entity)
    }

    override suspend fun addBoardSubscription(
        collectionId: String,
        sourceId: String,
        boardUrl: String,
        boardName: String,
    ) {
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
}
