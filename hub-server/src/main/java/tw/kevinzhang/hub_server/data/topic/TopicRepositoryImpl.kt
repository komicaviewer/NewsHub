package tw.kevinzhang.hub_server.data.topic

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import tw.kevinzhang.hub_server.data.board.BoardDao
import tw.kevinzhang.newshub.di.IoDispatcher
import javax.inject.Inject

class TopicRepositoryImpl @Inject constructor(
    private val dao: BoardDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
): TopicRepository {
    override fun getAllTopics() =
        dao.readAll().mapLatest {
            val topicSet = it.map { it.subscriber }.flatten().toSet()
            topicSet.map { Topic(it, it, 0) }
        }

    override suspend fun getTopic(id: String) = withContext(ioDispatcher) {
        Topic(id, id, 0)
    }
}