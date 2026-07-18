package tw.kevinzhang.newshub.ui.collection

import androidx.paging.PagingSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class MergedTimelinePagingSourceTest {
    @Test
    fun authenticationRequired_marks_source_for_a_foreground_notice() = runBlocking {
        val notifiedSourceIds = mutableListOf<String>()
        val source = object : Source {
            override val id = "gamer"
            override val name = "Gamer"
            override val language = "zh-TW"
            override val version = 1
            override val iconUrl: String? = null
            override val supportsCommentPagination = false
            override val alwaysUseRawImage = false
            override val needsLogin = false

            override suspend fun getBoards(): List<Board> = emptyList()

            override suspend fun getThreadSummaries(
                board: Board,
                page: Int,
            ): List<ThreadSummary> = throw AuthenticationRequiredException()

            override suspend fun getThread(summary: ThreadSummary): Thread = error("Not used")
        }
        val pagingSource = MergedTimelinePagingSource(
            subscriptions = listOf(
                BoardSubscriptionEntity(
                    id = "subscription",
                    collectionId = "collection",
                    sourceId = source.id,
                    boardUrl = "https://forum.gamer.com.tw/B.php?bsn=60076",
                    boardName = "場外休息區",
                    sortOrder = 0,
                ),
            ),
            sourceResolver = { source },
            onAuthenticationRequired = notifiedSourceIds::add,
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

        assertEquals(listOf(source.id), notifiedSourceIds)
        assertTrue(result is PagingSource.LoadResult.Error)
        assertTrue((result as PagingSource.LoadResult.Error).throwable is AuthenticationRequiredException)
    }
}
