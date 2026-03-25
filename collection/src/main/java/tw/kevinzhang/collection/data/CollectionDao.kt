package tw.kevinzhang.collection.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(entity: CollectionEntity)

    @Delete
    suspend fun deleteCollection(entity: CollectionEntity)

    @Query("SELECT * FROM board_subscriptions WHERE collectionId = :collectionId ORDER BY sortOrder")
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: BoardSubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(entity: BoardSubscriptionEntity)

    @Query("DELETE FROM board_subscriptions WHERE sourceId = :sourceId")
    suspend fun deleteSubscriptionsBySource(sourceId: String)

    @Query("DELETE FROM board_subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: String)

    @Query("DELETE FROM board_subscriptions WHERE collectionId = :collectionId")
    suspend fun deleteSubscriptionsByCollection(collectionId: String)
}
