package tw.kevinzhang.data.domain

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceIdentityDao {
    @Query("SELECT * FROM source_identities WHERE sourceKey = :sourceKey")
    suspend fun getByKey(sourceKey: String): SourceIdentityEntity?

    @Query("SELECT * FROM source_identities WHERE resolution = 'UNRESOLVED' ORDER BY sourceId")
    fun observeUnresolved(): Flow<List<SourceIdentityEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SourceIdentityEntity): Long
}
