package tw.kevinzhang.hub_server.data.topic

import kotlinx.coroutines.flow.Flow


interface TopicRepository {
    fun getAllTopics(): Flow<List<Topic>>
    suspend fun getTopic(id: String): Topic
}