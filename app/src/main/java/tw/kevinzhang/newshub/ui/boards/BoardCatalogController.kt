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
    private val onInvalidated: (SourceFailure) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(BoardCatalogState())
    val state = mutableState.asStateFlow()
    private var firstJob: Job? = null
    private var moreJob: Job? = null
    private var generation = 0L
    private var closed = false

    fun setQuery(query: BoardQuery) = loadFirst(query, false, query.text.isNotBlank())
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
                val page = source.getBoardPage(BoardPageRequest(query.copy(text = query.text.trim()), refresh = refresh, contextEpoch = context.epoch))
                ensureActive()
                if (closed || requestGeneration != generation) return@launch
                verifyContext(page)
                mutableState.value = BoardCatalogState(query, page.boards.distinctBy(Board::url), catalogInfo = page.catalogInfo,
                    nextPageToken = page.nextPageToken)
                cache.put(context, query, CachedBoardResult(page.boards, page.catalogInfo, System.currentTimeMillis()))
                // First page invalidates source-owned catalogs before any categories can be read.
                val categories = try { source.getBoardCategories().distinctBy(BoardCategory::id) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (revokesContext(SourceFailures.fromThrowable(error).code)) throw error
                        emptyList()
                    }
                ensureActive()
                if (!closed && requestGeneration == generation) mutableState.value = state.value.copy(categories = categories)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (closed || requestGeneration != generation) return@launch
                val failure = SourceFailures.fromThrowable(error, "board_page")
                if (revokesContext(failure.code)) { revoke(failure); return@launch }
                val authFailure = failure.code == SourceFailureCode.EXTENSION_UPDATE_REQUIRED
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
                val page = source.getBoardPage(BoardPageRequest(before.query.copy(text = before.query.text.trim()), token, contextEpoch = context.epoch))
                ensureActive()
                if (closed || requestGeneration != generation) return@launch
                verifyContext(page)
                require(page.nextPageToken != token) { "Source repeated its page token" }
                val merged = (before.boards + page.boards).distinctBy(Board::url)
                mutableState.value = state.value.copy(boards = merged, catalogInfo = page.catalogInfo ?: before.catalogInfo,
                    nextPageToken = page.nextPageToken, moreLoading = false, moreFailure = null)
                cache.put(context, before.query, CachedBoardResult(merged, state.value.catalogInfo, System.currentTimeMillis()))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val failure = SourceFailures.fromThrowable(error, "board_page")
                if (!closed && requestGeneration == generation && revokesContext(failure.code)) {
                    revoke(failure)
                    return@launch
                }
                if (!closed && requestGeneration == generation) mutableState.value = state.value.copy(
                    moreLoading = false, moreFailure = failure)
            }
        }
    }

    private fun verifyContext(page: BoardPage) {
        if (source is AuthenticatedSource && page.catalogInfo?.contextEpoch != context.epoch) {
            throw SourceFailureException(SourceFailure(SourceFailureCode.EXTENSION_UPDATE_REQUIRED, "board_page"))
        }
    }

    private fun revokesContext(code: SourceFailureCode) = code in setOf(
        SourceFailureCode.AUTH_REQUIRED, SourceFailureCode.AUTH_EXPIRED, SourceFailureCode.TRUST_INACTIVE)

    private fun revoke(failure: SourceFailure) {
        closed = true; generation++
        cache.invalidate(context.identityKey)
        mutableState.value = BoardCatalogState(query = state.value.query, firstPageFailure = failure)
        onInvalidated(failure)
    }

    fun close() {
        closed = true; generation++
        firstJob?.cancel(); moreJob?.cancel()
        cache.invalidate(context.identityKey)
        mutableState.value = BoardCatalogState()
    }
}
