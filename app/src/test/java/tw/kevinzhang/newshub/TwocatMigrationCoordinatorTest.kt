package tw.kevinzhang.newshub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.TwocatSourceIds
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader

class TwocatMigrationCoordinatorTest {
    @Test
    fun `current source migrates once even on later emissions or repeated starts`() = runBlocking {
        val sources = MutableStateFlow<List<Source>>(emptyList())
        val repository = RecordingRepository()
        val coordinator = TwocatMigrationCoordinator(
            extensionLoader = FakeLoader(sources),
            collectionRepository = repository,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        coordinator.start()
        coordinator.start()
        sources.value = listOf(source(TwocatSourceIds.CURRENT))
        sources.value = listOf(source(TwocatSourceIds.CURRENT), source("tw.other"))

        assertEquals(1, repository.calls)
    }

    @Test
    fun `legacy-only source never starts migration`() = runBlocking {
        val sources = MutableStateFlow<List<Source>>(listOf(source(TwocatSourceIds.LEGACY)))
        val repository = RecordingRepository()
        val coordinator = TwocatMigrationCoordinator(
            extensionLoader = FakeLoader(sources),
            collectionRepository = repository,
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

        coordinator.start()

        assertEquals(0, repository.calls)
    }

    private class FakeLoader(
        override val sourcesFlow: StateFlow<List<Source>>,
    ) : ExtensionLoader {
        override fun getAllSources(): List<Source> = sourcesFlow.value
        override fun getSource(id: String): Source? = sourcesFlow.value.firstOrNull { it.id == id }
    }

    private class RecordingRepository : CollectionRepository {
        var calls = 0

        override fun observeCollections(): Flow<List<CollectionEntity>> = emptyFlow()
        override fun observeSubscriptions(collectionId: String): Flow<List<BoardSubscriptionEntity>> = emptyFlow()
        override suspend fun createCollection(name: String, description: String, emoji: String): String = ""
        override suspend fun deleteCollection(id: String) = Unit
        override suspend fun getCollectionById(id: String): CollectionEntity? = null
        override suspend fun updateCollection(id: String, name: String, description: String, emoji: String) = Unit
        override suspend fun reorderCollections(orderedIds: List<String>) = Unit
        override suspend fun addBoardSubscription(sourceId: String, collectionId: String, boardUrl: String, boardName: String) = Unit
        override suspend fun removeBoardSubscription(subscriptionId: String) = Unit
        override suspend fun removeAllSubscriptionsForSource(sourceId: String) = Unit
        override suspend fun migrateSourceIds(legacySourceIds: Set<String>, currentSourceId: String) {
            calls++
        }
    }

    private fun source(id: String) = object : Source {
        override val id = id
        override val name = id
        override val language = "zh-TW"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoards(): List<Board> = emptyList()
        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = emptyList()
        override suspend fun getThread(summary: ThreadSummary): Thread = Thread("", null, null, emptyList())
        override suspend fun getComments(post: tw.kevinzhang.extension_api.model.Post, page: Int): CommentPage = CommentPage(emptyList(), false)
    }
}
