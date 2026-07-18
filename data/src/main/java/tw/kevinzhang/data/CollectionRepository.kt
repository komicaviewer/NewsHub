package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.CollectionEntity

interface CollectionRepository {
    fun observeCollections(): Flow<List<CollectionEntity>>
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>
    suspend fun createCollection(name: String, description: String = "", emoji: String = "📰"): String
    suspend fun deleteCollection(id: String)
    suspend fun getCollectionById(id: String): CollectionEntity?
    suspend fun updateCollection(id: String, name: String, description: String, emoji: String)
    suspend fun reorderCollections(orderedIds: List<String>)
    suspend fun addBoardSubscription(collectionId: String, sourceId: String, boardUrl: String, boardName: String)
    suspend fun removeBoardSubscription(subscriptionId: String)
    suspend fun removeAllSubscriptionsForSource(sourceId: String)
}
