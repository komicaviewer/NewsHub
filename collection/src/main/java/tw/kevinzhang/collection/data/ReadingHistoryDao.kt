package tw.kevinzhang.collection.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY readAt DESC")
    fun observeAll(): Flow<List<ReadingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingHistoryEntity)
}
