package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.ReadingHistoryRecord
import tw.kevinzhang.extension_api.model.ThreadSummary

interface ReadingHistoryRepository {
    fun observeReadingHistory(): Flow<List<ReadingHistoryRecord>>
    fun observeReadPostIds(sourceKey: String, threadId: String): Flow<Set<String>>
    suspend fun recordRead(
        sourceKey: String,
        summary: ThreadSummary,
        sourceName: String? = null,
        boardName: String? = null,
    )
    suspend fun markPostRead(sourceKey: String, threadId: String, postId: String)
    suspend fun markPostsRead(sourceKey: String, threadId: String, postIds: Collection<String>)
    suspend fun clearHistory()
}
