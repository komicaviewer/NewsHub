package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {
    @Transaction
    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPostRecord>>

    @Transaction
    @Query("SELECT * FROM saved_posts WHERE sourceKey = :sourceKey AND threadId = :threadId")
    fun observeById(sourceKey: String, threadId: String): Flow<SavedPostRecord?>

    @Query("SELECT * FROM saved_posts WHERE sourceKey = :sourceKey AND threadId = :threadId")
    suspend fun getById(sourceKey: String, threadId: String): SavedPostEntity?

    @Query("SELECT * FROM saved_posts")
    suspend fun getAll(): List<SavedPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedPostEntity)

    @Query("DELETE FROM saved_posts WHERE sourceKey = :sourceKey AND threadId = :threadId")
    suspend fun delete(sourceKey: String, threadId: String)

    @Query("DELETE FROM saved_posts")
    suspend fun deleteAll()
}
