package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.SourceIdentityEntity
import tw.kevinzhang.extension_api.SourceIdentity

interface SourceIdentityRepository {
    suspend fun register(identity: SourceIdentity): SourceIdentityEntity
    suspend fun getByKey(sourceKey: String): SourceIdentityEntity?
    fun observeUnresolved(): Flow<List<SourceIdentityEntity>>
}
