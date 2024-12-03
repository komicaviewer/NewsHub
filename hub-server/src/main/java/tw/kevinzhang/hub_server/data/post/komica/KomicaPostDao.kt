package tw.kevinzhang.hub_server.data.post.komica

import androidx.room.*

@Dao
interface KomicaPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<KomicaPost>)

    @Query("DELETE FROM komica_post")
    suspend fun clearAll()

    @Query("SELECT * FROM komica_post where threadUrl = :threadUrl and id = :headPostId")
    suspend fun readRePost(threadUrl: String, headPostId: String): KomicaPost

    @Query("SELECT * FROM komica_post where threadUrl = :threadUrl")
    suspend fun readPostThread(threadUrl: String): List<KomicaPost>

    @Query("SELECT * FROM komica_post where page = :page and threadUrl = :threadUrl and content like '%{\"id\":\"' || :headPostId || '\",\"type\":\"REPLY_TO\"}%'")
    suspend fun readRePostThread(threadUrl: String, headPostId: String, page: Int): List<KomicaPost>

    @Query("DELETE FROM komica_post WHERE threadUrl = :threadUrl")
    suspend fun clearPostThread(threadUrl: String)
}