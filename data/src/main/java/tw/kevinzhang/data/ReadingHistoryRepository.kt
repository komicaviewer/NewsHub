package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.ReadingHistoryEntity
import tw.kevinzhang.extension_api.model.ThreadSummary

interface ReadingHistoryRepository {
    fun observeReadingHistory(): Flow<List<ReadingHistoryEntity>>
    fun observeReadPostIds(sourceId: String, threadId: String): Flow<Set<String>>
    suspend fun recordRead(
        summary: ThreadSummary,
        sourceName: String? = null,
        boardName: String? = null,
    )
    suspend fun markPostRead(sourceId: String, threadId: String, postId: String)
    suspend fun markPostsRead(sourceId: String, threadId: String, postIds: Collection<String>)
    suspend fun clearHistory()
}
