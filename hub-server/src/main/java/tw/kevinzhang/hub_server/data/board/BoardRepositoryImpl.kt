package tw.kevinzhang.hub_server.data.board

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import tw.kevinzhang.newshub.di.IoDispatcher
import javax.inject.Inject

class BoardRepositoryImpl @Inject constructor(
    private val dao: BoardDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
): BoardRepository {

    override fun getAllBoards() =
        dao.readAll()

    override fun getSubscribed(subscriber: String) =
        getAllBoards().mapLatest { list ->
            list.filter { it.subscriber.contains(subscriber) }
        }

    override suspend fun subscribe(board: Board, subscriber: String) {
        val old = dao.read(board.url)
        old?.copy(subscriber = old.subscriber + subscriber)?.let {
            dao.upsert(it)
        }
    }

    override suspend fun unsubscribe(board: Board, subscriber: String) {
        val old = dao.read(board.url)
        old?.copy(subscriber = old.subscriber - subscriber)?.let {
            dao.upsert(it)
        }
    }

    override suspend fun clearAll() = withContext(ioDispatcher) {
        dao.clearAll()
    }
}