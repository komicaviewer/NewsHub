package tw.kevinzhang.collection

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.collection.data.BoardSubscriptionEntity
import tw.kevinzhang.collection.data.CollectionEntity

interface CollectionRepository {
    fun observeCollections(): Flow<List<CollectionEntity>>
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>
    suspend fun createCollection(name: String)
    suspend fun deleteCollection(id: String)
    suspend fun addBoardSubscription(collectionId: String, sourceId: String, boardUrl: String, boardName: String)
    suspend fun removeBoardSubscription(subscriptionId: String)
    suspend fun removeAllSubscriptionsForSource(sourceId: String)
}
