package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Transaction
    @Query("SELECT * FROM reading_history ORDER BY readAt DESC")
    fun observeAll(): Flow<List<ReadingHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()
}
