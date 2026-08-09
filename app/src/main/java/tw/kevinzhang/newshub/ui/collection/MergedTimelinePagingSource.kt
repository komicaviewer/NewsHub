package tw.kevinzhang.newshub.ui.collection

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tw.kevinzhang.data.domain.BoardSubscriptionRecord
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.data.domain.SourceResolution
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.ThreadSummary

data class SourceLoadFailure(
    val sourceId: String,
    val boardName: String,
    val cause: Throwable,
)

class MergedTimelinePagingSource(
    private val subscriptions: List<BoardSubscriptionRecord>,
    private val sourceResolver: (String) -> Source?,
    private val onAuthenticationRequired: (String) -> Unit,
    private val onSourceLoadFailures: (List<SourceLoadFailure>) -> Unit = {},
) : PagingSource<Int, ThreadSummary>() {

    override fun getRefreshKey(state: PagingState<Int, ThreadSummary>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ThreadSummary> {
        val page = params.key ?: 1
        val results = coroutineScope {
            subscriptions
                .map { sub ->
                    val entity = sub.subscription
                    val sourceId = sub.sourceIdentity.sourceId
                    val board = Board(sourceId = sourceId, url = entity.boardUrl, name = entity.boardName)
                    async {
                        try {
                            check(sub.sourceIdentity.resolution == SourceResolution.OFFICIAL)
                            val source = sourceResolver(sourceId)
                                ?: throw IllegalStateException("找不到來源：$sourceId")
                            val runtimeIdentity = source.sourceIdentity
                                ?: throw SecurityException("來源缺少 Host 驗證身分")
                            check(
                                CanonicalSourceIdentities.fromRuntimeIdentity(runtimeIdentity).sourceKey ==
                                    entity.sourceKey
                            ) { "來源擁有者不相符" }
                            SourceTimelineResult.Success(
                                source.getThreadSummaries(board, page)
                                    .map { it.copy(sourceIconUrl = source.iconUrl) },
                            )
                        } catch (error: AuthenticationRequiredException) {
                            onAuthenticationRequired(sourceId)
                            SourceTimelineResult.Failure(
                                SourceLoadFailure(sourceId, entity.boardName, error),
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            SourceTimelineResult.Failure(
                                SourceLoadFailure(sourceId, entity.boardName, error),
                            )
                        }
                    }
                }
                .awaitAll()
        }
        val failures = results.filterIsInstance<SourceTimelineResult.Failure>().map { it.failure }
        onSourceLoadFailures(failures)
        val successes = results.filterIsInstance<SourceTimelineResult.Success>()
        val summaries = successes.flatMap { it.summaries }

        // A failed board should not hide successful boards in the same collection. Only return an
        // error when there is nothing trustworthy to show at all.
        if (successes.isEmpty() && failures.isNotEmpty()) {
            return LoadResult.Error(MergedTimelineLoadException(failures))
        }

        // Per-batch sort only — no global ordering across page boundaries (by design)
        val sorted = summaries.sortedByDescending { it.createdAt }
        return LoadResult.Page(
            data = sorted,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (sorted.isEmpty()) null else page + 1,
        )
    }

    private sealed interface SourceTimelineResult {
        data class Success(val summaries: List<ThreadSummary>) : SourceTimelineResult
        data class Failure(val failure: SourceLoadFailure) : SourceTimelineResult
    }
}

class MergedTimelineLoadException(
    val failures: List<SourceLoadFailure>,
) : Exception("所有來源均無法載入")
