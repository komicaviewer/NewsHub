package tw.kevinzhang.hub_server.data.post.gamer

import androidx.room.*

@Dao
interface GamerPostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<GamerPost>)

    @Query("DELETE FROM gamer_post")
    suspend fun clearAll()

    @Query("SELECT * FROM gamer_post where threadUrl = :threadUrl and id = :headPostId")
    suspend fun readRePost(threadUrl: String, headPostId: String): GamerPost

    @Query("SELECT * FROM gamer_post where threadUrl = :threadUrl and page = :page")
    suspend fun readPostThread(threadUrl: String, page: Int): List<GamerPost>

    @Query("SELECT * FROM gamer_post where page = :page and threadUrl = :threadUrl and  content like '%{\"id\":\"' || :headPostId || '\",\"type\":\"REPLY_TO\"}%'")
    suspend fun readRePostThread(threadUrl: String, headPostId: String, page: Int): List<GamerPost>

    @Query("DELETE FROM gamer_post WHERE threadUrl = :threadUrl")
    suspend fun clearPostThread(threadUrl: String)
}