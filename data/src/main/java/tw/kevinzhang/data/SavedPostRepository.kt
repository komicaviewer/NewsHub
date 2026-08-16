package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.SavedPostEntity
import tw.kevinzhang.data.domain.SavedPostRecord

interface SavedPostRepository {
    fun observeSavedPosts(): Flow<List<SavedPostRecord>>
    fun observeSavedPost(sourceKey: String, threadId: String): Flow<SavedPostRecord?>
    suspend fun savePost(entity: SavedPostEntity)
    suspend fun unsavePost(sourceKey: String, threadId: String)
    suspend fun deleteAllSavedPosts()
}
