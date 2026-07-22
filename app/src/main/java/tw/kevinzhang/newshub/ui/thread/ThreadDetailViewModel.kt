package tw.kevinzhang.newshub.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.kevinzhang.data.ReadingHistoryRepository
import tw.kevinzhang.data.SavedPostRepository
import tw.kevinzhang.data.domain.ParagraphListConverter
import tw.kevinzhang.data.domain.SavedPostEntity
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadPage
import tw.kevinzhang.extension_api.model.ThreadPageMetadata
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.data.PreferenceStore
import tw.kevinzhang.newshub.data.ReadTrackingMode
import tw.kevinzhang.newshub.data.ReplyDisplayMode
import tw.kevinzhang.newshub.auth.SourceSessionManager
import java.io.File
import javax.inject.Inject

private const val COMMENTS_PAGE_SIZE = 5

data class CommentUiState(
    val visibleComments: List<Comment>,
    val hasMore: Boolean,
    val isLoading: Boolean = false,
)

@HiltViewModel
class ThreadDetailViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val preferenceStore: PreferenceStore,
    private val historyRepository: ReadingHistoryRepository,
    private val savedPostRepository: SavedPostRepository,
    private val sessionManager: SourceSessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val threadId: String = checkNotNull(savedStateHandle["threadId"]) {
        "ThreadDetailViewModel requires 'threadId' in SavedStateHandle"
    }
    val sourceId: String = checkNotNull(savedStateHandle["sourceId"]) {
        "ThreadDetailViewModel requires 'sourceId' in SavedStateHandle"
    }
    private val boardUrl: String = checkNotNull(savedStateHandle["boardUrl"]) {
        "ThreadDetailViewModel requires 'boardUrl' in SavedStateHandle"
    }
    private val boardName: String? = savedStateHandle["boardName"]
    private val threadTitle: String? = savedStateHandle["threadTitle"]

    private val _thread = MutableStateFlow<Thread?>(null)
    val thread = _thread.asStateFlow()

    private val _sourceName = MutableStateFlow("")
    val sourceName = _sourceName.asStateFlow()

    val sourceBoardLabel: StateFlow<String> = _sourceName
        .map { source ->
            listOfNotNull(source.takeIf(String::isNotBlank), boardName?.takeIf(String::isNotBlank))
                .joinToString(" · ")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val threadUrl: StateFlow<String?> = _thread
        .map { it?.url }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val previewPost = MutableStateFlow<Post?>(null)

    private val _alwaysUseRawImage = MutableStateFlow(false)
    val alwaysUseRawImage = _alwaysUseRawImage.asStateFlow()

    val replyDisplayMode: StateFlow<ReplyDisplayMode> = preferenceStore.observable
        .map { it.readingPreferences.replyDisplayMode }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReplyDisplayMode.CONTEXTUAL,
        )

    val readTrackingMode: StateFlow<ReadTrackingMode> = preferenceStore.observable
        .map { it.readingPreferences.readTrackingMode }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReadTrackingMode.POST_VISIBLE,
        )

    val readPostIds: StateFlow<Set<String>> = historyRepository
        .observeReadPostIds(sourceId, threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var cachedSource: Source? = null

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    private val _threadPaging = MutableStateFlow(ThreadPagingState())
    internal val threadPaging = _threadPaging.asStateFlow()
    private var threadLoadGeneration = 0L

    val isSaved: StateFlow<Boolean> = savedPostRepository
        .observeSavedPost(sourceId, threadId)
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isSavingScreenshots = MutableStateFlow(false)
    val isSavingScreenshots = _isSavingScreenshots.asStateFlow()

    val authenticationRequiredNotice = sessionManager.authenticationRequiredNotice

    fun consumeAuthenticationRequiredNotice(sourceId: String) {
        sessionManager.consumeAuthenticationRequiredNotice(sourceId)
    }

    private data class InternalCommentState(
        val visibleComments: List<Comment>,
        val hasMore: Boolean,
        val isLoading: Boolean = false,
        // network pagination
        val nextPage: Int = 2,
        // local pagination
        val allLocalComments: List<Comment> = emptyList(),
    )

    private val _commentStates = MutableStateFlow<Map<String, InternalCommentState>>(emptyMap())
    val commentStates: StateFlow<Map<String, CommentUiState>> = _commentStates
        .map { states ->
            states.mapValues { (_, v) ->
                CommentUiState(v.visibleComments, v.hasMore, v.isLoading)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var historySummary: ThreadSummary? = null
    private var historyRecorded = false
    private val pendingReadPostIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val source = extensionLoader.getSource(sourceId)
            if (source == null) {
                _loadError.value = "找不到這個內容來源，可能需要重新安裝擴充套件。"
                _isLoading.value = false
                return@launch
            }
            cachedSource = source
            _sourceName.value = source.name
            _alwaysUseRawImage.value = source.alwaysUseRawImage
            loadThreadInForeground(source)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val source = cachedSource ?: extensionLoader.getSource(sourceId)
            if (source == null) {
                _loadError.value = "找不到這個內容來源，可能需要重新安裝擴充套件。"
                _isLoading.value = false
                return@launch
            }
            cachedSource = source
            _sourceName.value = source.name
            _alwaysUseRawImage.value = source.alwaysUseRawImage
            loadThreadInForeground(source)
        }
    }

    private suspend fun loadThreadInForeground(source: Source) {
        val generation = ++threadLoadGeneration
        _isLoading.value = true
        _loadError.value = null
        // A refresh supersedes an in-flight append; its result must not update the new page set.
        _threadPaging.update { it.copy(isAppending = false, appendError = null) }
        try {
            val page = getThreadPage(source, pageToken = null)
            if (generation != threadLoadGeneration) return
            val thread = prepareThread(page, source)
            val initialCommentStates = buildCommentStates(source, thread.posts)
            if (generation != threadLoadGeneration) return
            updateHistorySummary(thread, source)
            _thread.value = thread
            _threadPaging.value = ThreadPagingState().forInitialPage(page.nextPageToken)
            _commentStates.value = initialCommentStates
            markLoadedPostsReadIfNeeded(thread.posts)
        } catch (_: AuthenticationRequiredException) {
            sessionManager.notifyAuthenticationRequired(sourceId)
        } catch (error: Exception) {
            if (generation != threadLoadGeneration) return
            _loadError.value = error.localizedMessage?.takeIf(String::isNotBlank)
                ?: "無法載入討論串，請稍後再試。"
        } finally {
            if (generation == threadLoadGeneration) _isLoading.value = false
        }
    }

    private fun threadSummary(): ThreadSummary = ThreadSummary(
        sourceId = sourceId,
        boardUrl = boardUrl,
        id = threadId,
        title = threadTitle,
        author = null,
        createdAt = null,
        commentCount = null,
        thumbnail = null,
        rawImage = null,
        previewContent = emptyList(),
    )

    private suspend fun getThreadPage(source: Source, pageToken: String?): ThreadPage = try {
        source.getThreadPage(threadSummary(), pageToken)
    } catch (error: AbstractMethodError) {
        // Older extension APKs do not have the new interface method yet.
        if (pageToken != null) throw error
        val thread = source.getThread(threadSummary())
        ThreadPage(
            posts = thread.posts,
            nextPageToken = null,
            metadata = ThreadPageMetadata(thread.id, thread.url, thread.title),
        )
    }

    private fun prepareThread(page: ThreadPage, source: Source): Thread {
        val metadata = page.metadata
        check(metadata == null || metadata.id.isBlank() || metadata.id == threadId) {
            "內容來源回傳了不相符的討論串。"
        }
        return Thread(
            // A few legacy sources leave the thread ID empty; the navigation ID is authoritative.
            id = metadata?.id?.ifBlank { threadId } ?: threadId,
            url = metadata?.url ?: source.getWebUrl(threadSummary()),
            title = metadata?.title ?: threadTitle,
            posts = page.posts.map { it.copy(sourceIconUrl = source.iconUrl) },
        )
    }

    private fun updateHistorySummary(thread: Thread, source: Source) {
        val firstPost = thread.posts.firstOrNull()
        val firstImage = firstPost?.content?.filterIsInstance<Paragraph.ImageInfo>()?.firstOrNull()
        historySummary = ThreadSummary(
            sourceId = sourceId,
            boardUrl = boardUrl,
            id = threadId,
            title = thread.title ?: threadTitle,
            author = firstPost?.author,
            createdAt = firstPost?.createdAt,
            commentCount = null,
            replyCount = firstPost?.replyCount,
            thumbnail = firstPost?.thumbnail ?: firstImage?.thumb,
            rawImage = firstImage?.raw,
            previewContent = firstPost?.content?.take(3) ?: emptyList(),
            sourceIconUrl = source.iconUrl,
        )
    }

    private suspend fun markLoadedPostsReadIfNeeded(posts: List<Post>) {
        if (
            preferenceStore.observable.first().readingPreferences.readTrackingMode ==
            ReadTrackingMode.THREAD_OPENED
        ) {
            markPostsRead(posts.map(Post::id))
        }
    }

    fun loadMorePosts() {
        val paging = _threadPaging.value
        val appending = paging.startAppend() ?: return
        val pageToken = paging.nextPageToken ?: return
        val generation = threadLoadGeneration
        _threadPaging.value = appending

        viewModelScope.launch {
            val source = cachedSource ?: extensionLoader.getSource(sourceId)
            if (source == null) {
                if (generation == threadLoadGeneration) {
                    _threadPaging.update {
                        it.appendFailed("找不到這個內容來源，可能需要重新安裝擴充套件。")
                    }
                }
                return@launch
            }
            try {
                val page = getThreadPage(source, pageToken)
                if (generation != threadLoadGeneration) return@launch
                val currentThread = _thread.value ?: return@launch
                val preparedPage = prepareThread(page, source)
                val mergedPosts = mergePostsById(currentThread.posts, preparedPage.posts)
                val addedPosts = preparedPage.posts.filter { post ->
                    currentThread.posts.none { it.id == post.id }
                }
                _thread.value = currentThread.copy(posts = mergedPosts)
                _threadPaging.update { current ->
                    if (current.nextPageToken == pageToken) {
                        current.appendSucceeded(pageToken, page.nextPageToken)
                    } else {
                        current
                    }
                }
                try {
                    initializeCommentStatesForNewPosts(
                        source = source,
                        posts = addedPosts,
                        expectedGeneration = generation,
                    )
                } catch (_: AuthenticationRequiredException) {
                    if (generation != threadLoadGeneration) return@launch
                    _commentStates.update { states ->
                        states + addedPosts
                            .filter { it.id !in states }
                            .associate { it.id to InternalCommentState(emptyList(), hasMore = false) }
                    }
                    sessionManager.notifyAuthenticationRequired(sourceId)
                }
                if (generation == threadLoadGeneration) markLoadedPostsReadIfNeeded(addedPosts)
            } catch (_: AuthenticationRequiredException) {
                if (generation == threadLoadGeneration) {
                    _threadPaging.update { it.appendFailed("需要登入才能載入更多貼文。") }
                    sessionManager.notifyAuthenticationRequired(sourceId)
                }
            } catch (error: Exception) {
                if (generation == threadLoadGeneration) {
                    _threadPaging.update {
                        it.appendFailed(
                            error.localizedMessage?.takeIf(String::isNotBlank)
                                ?: "無法載入更多貼文，請稍後再試。"
                        )
                    }
                }
            }
        }
    }

    fun setReplyDisplayMode(mode: ReplyDisplayMode) {
        viewModelScope.launch { preferenceStore.setReplyDisplayMode(mode) }
    }

    fun setReadTrackingMode(mode: ReadTrackingMode) {
        viewModelScope.launch { preferenceStore.setReadTrackingMode(mode) }
    }

    fun markPostRead(postId: String) {
        if (postId in readPostIds.value || !pendingReadPostIds.add(postId)) {
            viewModelScope.launch { recordHistoryIfNeeded() }
            return
        }
        viewModelScope.launch {
            recordHistoryIfNeeded()
            historyRepository.markPostRead(sourceId, threadId, postId)
        }
    }

    fun markAllPostsRead() {
        markPostsRead(_thread.value?.posts.orEmpty().map(Post::id))
    }

    private fun markPostsRead(postIds: Collection<String>) {
        val unreadIds = postIds.filterNot { it in readPostIds.value || it in pendingReadPostIds }
        if (unreadIds.isEmpty()) {
            viewModelScope.launch { recordHistoryIfNeeded() }
            return
        }
        pendingReadPostIds += unreadIds
        viewModelScope.launch {
            recordHistoryIfNeeded()
            historyRepository.markPostsRead(sourceId, threadId, unreadIds)
        }
    }

    private suspend fun recordHistoryIfNeeded() {
        if (historyRecorded) return
        val summary = historySummary ?: return
        historyRecorded = true
        historyRepository.recordRead(
            summary = summary,
            sourceName = cachedSource?.name,
            boardName = boardName,
        )
    }

    private suspend fun initializeCommentStatesForNewPosts(
        source: Source,
        posts: List<Post>,
        expectedGeneration: Long = threadLoadGeneration,
    ) {
        val newPosts = posts.filter { it.id !in _commentStates.value }
        if (newPosts.isEmpty()) return
        val newStates = buildCommentStates(source, newPosts)
        if (expectedGeneration != threadLoadGeneration) return
        _commentStates.update { existing ->
            existing + newStates.filterKeys { it !in existing }
        }
    }

    private suspend fun buildCommentStates(
        source: Source,
        posts: List<Post>,
    ): Map<String, InternalCommentState> {
        return if (source.supportsCommentPagination) {
            coroutineScope {
                posts.map { post ->
                    async {
                        post.id to try {
                            val page = source.getComments(post, 1)
                            InternalCommentState(
                                visibleComments = page.comments,
                                hasMore = page.hasMore,
                            )
                        } catch (error: AuthenticationRequiredException) {
                            throw error
                        } catch (_: Exception) {
                            InternalCommentState(emptyList(), false)
                        }
                    }
                }.awaitAll().toMap()
            }
        } else {
            posts.associate { post ->
                post.id to InternalCommentState(
                    visibleComments = post.comments.take(COMMENTS_PAGE_SIZE),
                    hasMore = post.comments.size > COMMENTS_PAGE_SIZE,
                    allLocalComments = post.comments,
                )
            }
        }
    }

    fun loadMoreComments(postId: String) {
        val state = _commentStates.value[postId] ?: return
        if (state.isLoading || !state.hasMore) return

        viewModelScope.launch {
            _commentStates.update { it + (postId to state.copy(isLoading = true)) }

            val source = cachedSource ?: return@launch

            if (source.supportsCommentPagination) {
                val post = _thread.value?.posts?.find { it.id == postId } ?: return@launch
                val result = try {
                    source.getComments(post, state.nextPage)
                } catch (_: AuthenticationRequiredException) {
                    sessionManager.notifyAuthenticationRequired(sourceId)
                    CommentPage(emptyList(), false)
                } catch (_: Exception) {
                    CommentPage(emptyList(), false)
                }
                _commentStates.update { states ->
                    val current = states[postId] ?: return@update states
                    states + (postId to current.copy(
                        visibleComments = current.visibleComments + result.comments,
                        hasMore = result.hasMore,
                        nextPage = current.nextPage + 1,
                        isLoading = false,
                    ))
                }
            } else {
                val newCount = state.visibleComments.size + COMMENTS_PAGE_SIZE
                _commentStates.update { states ->
                    val current = states[postId] ?: return@update states
                    states + (postId to current.copy(
                        visibleComments = current.allLocalComments.take(newCount),
                        hasMore = current.allLocalComments.size > newCount,
                        isLoading = false,
                    ))
                }
            }
        }
    }

    fun onReplyToClick(targetId: String) {
        previewPost.value = _thread.value?.posts?.find { it.id == targetId }
    }

    fun dismissPreview() {
        previewPost.value = null
    }

    fun requestToggleSave(filesDir: File) {
        if (isSaved.value) {
            viewModelScope.launch {
                savedPostRepository.unsavePost(sourceId, threadId)
                deleteScreenshots(filesDir)
            }
        } else {
            _isSavingScreenshots.value = true
        }
    }

    fun onScreenshotsCaptured(screenshotPaths: List<String>) {
        val thread = _thread.value ?: run {
            _isSavingScreenshots.value = false
            return
        }
        val firstPost = thread.posts.firstOrNull()
        val firstImage = firstPost?.content
            ?.filterIsInstance<Paragraph.ImageInfo>()
            ?.firstOrNull()
        val converter = ParagraphListConverter()
        val entity = SavedPostEntity(
            sourceId = sourceId,
            sourceName = cachedSource?.name,
            threadId = threadId,
            boardUrl = boardUrl,
            boardName = boardName,
            title = thread.title ?: threadTitle,
            author = firstPost?.author,
            createdAt = firstPost?.createdAt,
            commentCount = null,
            replyCount = firstPost?.replyCount,
            thumbnail = firstPost?.thumbnail ?: firstImage?.thumb,
            rawImage = firstImage?.raw,
            previewContent = converter.toJson(firstPost?.content?.take(3) ?: emptyList()),
            sourceIconUrl = cachedSource?.iconUrl,
            threadUrl = thread.url,
            savedAt = System.currentTimeMillis(),
            screenshotPaths = Gson().toJson(screenshotPaths),
        )
        viewModelScope.launch {
            savedPostRepository.savePost(entity)
            _isSavingScreenshots.value = false
        }
    }

    private suspend fun deleteScreenshots(filesDir: File) {
        withContext(Dispatchers.IO) {
            File(filesDir, "saved_posts/${sourceId}_${threadId}").deleteRecursively()
        }
    }
}
