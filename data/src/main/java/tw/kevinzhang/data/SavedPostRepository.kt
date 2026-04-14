package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.SavedPostEntity

interface SavedPostRepository {
    fun observeSavedPosts(): Flow<List<SavedPostEntity>>
    fun observeSavedPost(sourceId: String, threadId: String): Flow<SavedPostEntity?>
    suspend fun savePost(entity: SavedPostEntity)
    suspend fun unsavePost(sourceId: String, threadId: String)
    suspend fun deleteAllSavedPosts()
}
