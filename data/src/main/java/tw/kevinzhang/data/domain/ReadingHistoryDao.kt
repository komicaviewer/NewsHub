package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY readAt DESC")
    fun observeAll(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE sourceId = :sourceId")
    suspend fun getBySource(sourceId: String): List<ReadingHistoryEntity>

    @Query("SELECT * FROM reading_history WHERE sourceId = :sourceId AND threadId = :threadId")
    suspend fun getById(sourceId: String, threadId: String): ReadingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE sourceId = :sourceId AND threadId = :threadId")
    suspend fun delete(sourceId: String, threadId: String)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()
}
