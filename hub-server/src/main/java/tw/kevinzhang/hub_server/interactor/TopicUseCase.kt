package tw.kevinzhang.hub_server.interactor

import tw.kevinzhang.hub_server.data.topic.TopicRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TopicUseCase @Inject constructor(
    private val topicRepository: TopicRepository,
) {
    suspend fun get(id: String) =
        topicRepository.getTopic(id)

    fun getAll() =
        topicRepository.getAllTopics()
}