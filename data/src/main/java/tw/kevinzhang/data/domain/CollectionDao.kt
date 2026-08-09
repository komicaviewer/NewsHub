package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
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

    @Update
    suspend fun updateCollection(entity: CollectionEntity)

    @Transaction
    @Query("SELECT * FROM board_subscriptions WHERE collectionId = :collectionId ORDER BY sortOrder")
    fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionRecord>>

    @Query("SELECT COUNT(*) FROM board_subscriptions WHERE collectionId = :collectionId AND sourceKey = :sourceKey AND boardUrl = :boardUrl")
    suspend fun countSubscription(collectionId: String, sourceKey: String, boardUrl: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: BoardSubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(entity: BoardSubscriptionEntity)

    @Query("DELETE FROM board_subscriptions WHERE sourceKey = :sourceKey")
    suspend fun deleteSubscriptionsBySource(sourceKey: String)

    @Query("DELETE FROM board_subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: String)

    @Query("DELETE FROM board_subscriptions WHERE collectionId = :collectionId")
    suspend fun deleteSubscriptionsByCollection(collectionId: String)
}
