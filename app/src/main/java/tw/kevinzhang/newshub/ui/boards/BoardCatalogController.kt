package tw.kevinzhang.newshub.ui.boards

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.kevinzhang.extension_api.*
import tw.kevinzhang.extension_api.model.*
import tw.kevinzhang.newshub.ui.collection.BoardCatalogCache
import tw.kevinzhang.newshub.ui.collection.CachedBoardResult

data class BoardCatalogState(
    val query: BoardQuery = BoardQuery(),
    val boards: List<Board> = emptyList(),
    val categories: List<BoardCategory> = emptyList(),
    val catalogInfo: BoardCatalogInfo? = null,
    val firstPageLoading: Boolean = false,
    val moreLoading: Boolean = false,
    val firstPageFailure: SourceFailure? = null,
    val moreFailure: SourceFailure? = null,
    val nextPageToken: String? = null,
    val isRecentFallback: Boolean = false,
)

/** One source and one credential generation. close() revokes publication even for late replies. */
class BoardCatalogController(
    private val source: Source,
    private val context: BoardCatalogContext,
    private val cache: BoardCatalogCache,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(BoardCatalogState())
    val state = mutableState.asStateFlow()
    private var firstJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var closed = false

    fun setQuery(query: BoardQuery) = loadFirst(query.copy(text = query.text.trim()), false, query.text.isNotBlank())
    fun refresh() = loadFirst(state.value.query, true, false)
    fun retry() = loadFirst(state.value.query, false, false)

    private fun loadFirst(query: BoardQuery, refresh: Boolean, debounce: Boolean) {
        if (closed) return
        val requestGeneration = ++generation
        firstJob?.cancel(); moreJob?.cancel()
        mutableState.value = BoardCatalogState(query = query, firstPageLoading = true)
        firstJob = scope.launch {
            if (debounce) delay(300)
            try {
                val page = source.getBoardPage(BoardPageRequest(query, refresh = refresh, contextEpoch = context.epoch))
                ensureActive()
                if (closed || requestGeneration != generation) return@launch
                mutableState.value = BoardCatalogState(query, page.boards.distinctBy(Board::url), catalogInfo = page.catalogInfo,
                    nextPageToken = page.nextPageToken)
                cache.put(context, query, CachedBoardResult(page.boards, page.catalogInfo, System.currentTimeMillis()))
                // First page invalidates source-owned catalogs before any categories can be read.
                val categories = try { source.getBoardCategories().distinctBy(BoardCategory::id) }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { emptyList() }
                ensureActive()
                if (!closed && requestGeneration == generation) mutableState.value = state.value.copy(categories = categories)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (closed || requestGeneration != generation) return@launch
                val failure = SourceFailures.fromThrowable(error, "board_page")
                val authFailure = failure.code in setOf(SourceFailureCode.AUTH_REQUIRED, SourceFailureCode.AUTH_EXPIRED, SourceFailureCode.TRUST_INACTIVE)
                if (authFailure) cache.invalidate(context.identityKey)
                val cached = if (authFailure) null else cache.get(context, query)
                mutableState.value = BoardCatalogState(query, cached?.boards.orEmpty(),
                    catalogInfo = cached?.catalogInfo?.copy(isStale = true), firstPageFailure = failure,
                    isRecentFallback = cached != null)
            }
        }
    }

    fun loadMore() {
        val before = state.value
        val token = before.nextPageToken ?: return
        if (closed || before.moreLoading || before.firstPageLoading) return
        val requestGeneration = generation
        mutableState.value = before.copy(moreLoading = true, moreFailure = null)
        moreJob = scope.launch {
            try {
                val page = source.getBoardPage(BoardPageRequest(before.query, token, contextEpoch = context.epoch))
                ensureActive()
                if (closed || requestGeneration != generation) return@launch
                require(page.nextPageToken != token) { "Source repeated its page token" }
                val merged = (before.boards + page.boards).distinctBy(Board::url)
                mutableState.value = state.value.copy(boards = merged, catalogInfo = page.catalogInfo ?: before.catalogInfo,
                    nextPageToken = page.nextPageToken, moreLoading = false, moreFailure = null)
                cache.put(context, before.query, CachedBoardResult(merged, state.value.catalogInfo, System.currentTimeMillis()))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (!closed && requestGeneration == generation) mutableState.value = state.value.copy(
                    moreLoading = false, moreFailure = SourceFailures.fromThrowable(error, "board_page"))
            }
        }
    }

    fun close() {
        closed = true; generation++
        firstJob?.cancel(); moreJob?.cancel()
        cache.invalidate(context.identityKey)
        mutableState.value = BoardCatalogState()
    }
}
