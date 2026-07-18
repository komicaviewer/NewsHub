package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {
    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedPostEntity>>

    @Query("SELECT * FROM saved_posts WHERE sourceId = :sourceId AND threadId = :threadId")
    fun observeById(sourceId: String, threadId: String): Flow<SavedPostEntity?>

    @Query("SELECT * FROM saved_posts WHERE sourceId = :sourceId AND threadId = :threadId")
    suspend fun getById(sourceId: String, threadId: String): SavedPostEntity?

    @Query("SELECT * FROM saved_posts")
    suspend fun getAll(): List<SavedPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedPostEntity)

    @Query("DELETE FROM saved_posts WHERE sourceId = :sourceId AND threadId = :threadId")
    suspend fun delete(sourceId: String, threadId: String)

    @Query("DELETE FROM saved_posts")
    suspend fun deleteAll()
}
