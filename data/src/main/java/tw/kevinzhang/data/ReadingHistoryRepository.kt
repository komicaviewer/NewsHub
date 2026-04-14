package tw.kevinzhang.data

import kotlinx.coroutines.flow.Flow
import tw.kevinzhang.data.domain.ReadingHistoryEntity
import tw.kevinzhang.extension_api.model.ThreadSummary

interface ReadingHistoryRepository {
    fun observeReadingHistory(): Flow<List<ReadingHistoryEntity>>
    suspend fun recordRead(summary: ThreadSummary)
    suspend fun clearHistory()
}
