package tw.kevinzhang.newshub.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tw.kevinzhang.collection.ReadingHistoryRepository
import tw.kevinzhang.collection.SavedPostRepository
import tw.kevinzhang.collection.data.ParagraphListConverter
import tw.kevinzhang.collection.data.SavedPostEntity
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.data.PreferenceStore
import com.google.gson.Gson
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
    private val threadTitle: String? = savedStateHandle["threadTitle"]

    private val _thread = MutableStateFlow<Thread?>(null)
    val thread = _thread.asStateFlow()

    val threadUrl: StateFlow<String?> = _thread
        .map { it?.url }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val previewPost = MutableStateFlow<Post?>(null)

    private val _alwaysUseRawImage = MutableStateFlow(false)
    val alwaysUseRawImage = _alwaysUseRawImage.asStateFlow()

    private val _useWebViewPosts = MutableStateFlow<Set<String>>(emptySet())
    val useWebViewPosts = _useWebViewPosts.asStateFlow()

    val webViewTextZoom: StateFlow<Int> = preferenceStore.observable
        .map { it.webViewTextZoom }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)

    private var cachedSource: Source? = null

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val isSaved: StateFlow<Boolean> = savedPostRepository
        .observeSavedPost(sourceId, threadId)
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isSavingScreenshots = MutableStateFlow(false)
    val isSavingScreenshots = _isSavingScreenshots.asStateFlow()

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

    init {
        viewModelScope.launch {
            val source = extensionLoader.getSource(sourceId) ?: return@launch
            cachedSource = source
            _alwaysUseRawImage.value = source.alwaysUseRawImage
            loadThread(source)
        }
    }

    fun refresh() {
        val source = cachedSource ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            loadThread(source)
            _isRefreshing.value = false
        }
    }

    private suspend fun loadThread(source: Source) {
        val summary = ThreadSummary(
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
        val rawThread = source.getThread(summary)
        val thread = rawThread.copy(
            posts = rawThread.posts.map { it.copy(sourceIconUrl = source.iconUrl) }
        )
        _thread.value = thread
        _commentStates.value = buildInitialCommentStates(source, thread)
        val firstPost = thread.posts.firstOrNull()
        val firstImage = firstPost?.content?.filterIsInstance<Paragraph.ImageInfo>()?.firstOrNull()
        historyRepository.recordRead(
            ThreadSummary(
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
        )
    }

    private suspend fun buildInitialCommentStates(
        source: Source,
        thread: Thread,
    ): Map<String, InternalCommentState> {
        return if (source.supportsCommentPagination) {
            coroutineScope {
                thread.posts.map { post ->
                    async {
                        post.id to try {
                            val page = source.getComments(post, 1)
                            InternalCommentState(
                                visibleComments = page.comments,
                                hasMore = page.hasMore,
                            )
                        } catch (_: Exception) {
                            InternalCommentState(emptyList(), false)
                        }
                    }
                }.awaitAll().toMap()
            }
        } else {
            thread.posts.associate { post ->
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

    fun enableWebViewForPost(postId: String) {
        _useWebViewPosts.update { it + postId }
    }

    fun setWebViewTextZoom(zoom: Int) {
        viewModelScope.launch { preferenceStore.setWebViewTextZoom(zoom) }
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
            threadId = threadId,
            boardUrl = boardUrl,
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
