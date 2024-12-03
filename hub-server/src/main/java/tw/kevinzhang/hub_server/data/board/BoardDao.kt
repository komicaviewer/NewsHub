package tw.kevinzhang.hub_server.data.board

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {

    @Query("SELECT * FROM board")
    fun readAll(): Flow<List<Board>>

    @Query("SELECT * FROM board WHERE url = :url")
    suspend fun read(url: String): Board?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<Board>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(new: Board)

    @Query("DELETE FROM board")
    suspend fun clearAll()
}