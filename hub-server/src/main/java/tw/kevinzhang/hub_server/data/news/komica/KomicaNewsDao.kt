package tw.kevinzhang.hub_server.data.news.komica

import androidx.room.*

@Dao
interface KomicaNewsDao {

    @Query("SELECT * FROM komica_news where page = :page and boardUrl = :boardUrl")
    suspend fun readAll(boardUrl: String, page: Int): List<KomicaNews>

    @Query("SELECT * FROM komica_news where threadUrl = :url")
    suspend fun readNews(url: String): KomicaNews

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<KomicaNews>)

    @Query("DELETE FROM komica_news")
    suspend fun clearAll()
}