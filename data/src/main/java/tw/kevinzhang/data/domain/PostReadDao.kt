package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostReadDao {
    @Query(
        "SELECT postId FROM post_read_states " +
            "WHERE sourceKey = :sourceKey AND threadId = :threadId",
    )
    fun observeReadPostIds(sourceKey: String, threadId: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PostReadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PostReadEntity>)
}
