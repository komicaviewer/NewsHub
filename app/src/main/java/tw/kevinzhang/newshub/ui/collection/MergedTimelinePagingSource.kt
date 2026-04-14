package tw.kevinzhang.newshub.ui.collection

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tw.kevinzhang.data.domain.BoardSubscriptionEntity
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.ThreadSummary

class MergedTimelinePagingSource(
    private val subscriptions: List<BoardSubscriptionEntity>,
    private val sourceResolver: (String) -> Source?,
) : PagingSource<Int, ThreadSummary>() {

    override fun getRefreshKey(state: PagingState<Int, ThreadSummary>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ThreadSummary> {
        val page = params.key ?: 1
        // Note: if any single source throws, the entire page load fails (all-or-nothing).
        // This is intentional — partial results from a failing source could confuse timeline ordering.
        return try {
            val results = coroutineScope {
                subscriptions
                    .mapNotNull { sub ->
                        val source = sourceResolver(sub.sourceId) ?: return@mapNotNull null
                        val board = Board(sourceId = sub.sourceId, url = sub.boardUrl, name = sub.boardName)
                        async {
                            source.getThreadSummaries(board, page)
                                .map { it.copy(sourceIconUrl = source.iconUrl) }
                        }
                    }
                    .awaitAll()
                    .flatten()
            }
            // Per-batch sort only — no global ordering across page boundaries (by design)
            val sorted = results.sortedByDescending { it.createdAt }
            LoadResult.Page(
                data = sorted,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (sorted.isEmpty()) null else page + 1,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
