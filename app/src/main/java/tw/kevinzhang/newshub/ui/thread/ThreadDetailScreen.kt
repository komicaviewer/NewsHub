package tw.kevinzhang.newshub.ui.thread

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.filterRepliesBy
import tw.kevinzhang.newshub.data.ReadTrackingMode
import tw.kevinzhang.newshub.data.ReplyDisplayMode
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.component.BodySmallText
import tw.kevinzhang.newshub.ui.component.LabelMediumText
import tw.kevinzhang.newshub.ui.component.LabelSmallText
import tw.kevinzhang.newshub.ui.component.Small
import tw.kevinzhang.newshub.ui.component.View
import tw.kevinzhang.newshub.ui.component.appClickable
import tw.kevinzhang.newshub.ui.component.resourceModelOrNull
import tw.kevinzhang.newshub.ui.component.openExternalLink
import tw.kevinzhang.newshub.ui.component.gallery.PostGallery
import tw.kevinzhang.newshub.ui.component.swipeToGoBack

private const val HIGHLIGHT_DURATION_MS = 1500

private data class GalleryRequest(
    val postId: String,
    val startIndex: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToBoards: () -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel(),
) {
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val previewPost by viewModel.previewPost.collectAsStateWithLifecycle()
    val commentStates by viewModel.commentStates.collectAsStateWithLifecycle()
    val alwaysUseRawImage by viewModel.alwaysUseRawImage.collectAsStateWithLifecycle()
    val sourceBoardLabel by viewModel.sourceBoardLabel.collectAsStateWithLifecycle()
    val replyDisplayMode by viewModel.replyDisplayMode.collectAsStateWithLifecycle()
    val readTrackingMode by viewModel.readTrackingMode.collectAsStateWithLifecycle()
    val readPostIds by viewModel.readPostIds.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val threadPaging by viewModel.threadPaging.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val isSavingScreenshots by viewModel.isSavingScreenshots.collectAsStateWithLifecycle()
    val authenticationRequiredNotice by viewModel.authenticationRequiredNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var repliesDialogForPostId by remember { mutableStateOf<String?>(null) }
    var galleryRequest by remember { mutableStateOf<GalleryRequest?>(null) }
    var highlightedPostId by remember { mutableStateOf<String?>(null) }
    var preferencesMenuExpanded by remember { mutableStateOf(false) }

    val displayedPosts = remember(thread?.posts, replyDisplayMode) {
        val posts = thread?.posts.orEmpty()
        if (replyDisplayMode == ReplyDisplayMode.NESTED) {
            posts.asThreadedPosts(maxDepth = 3)
        } else {
            posts.map { post ->
                ThreadedPost(post = post, actualDepth = 0, visualDepth = 0, parentId = null)
            }
        }
    }
    val jumpToPost: (String) -> Unit = { postId ->
        val postIndex = displayedPosts.indexOfFirst { it.post.id == postId }
        if (postIndex >= 0) {
            coroutineScope.launch {
                val returnIndex = listState.firstVisibleItemIndex
                val returnOffset = listState.firstVisibleItemScrollOffset
                val errorOffset = if (loadError != null && thread != null) 1 else 0
                listState.animateScrollToItem(postIndex + errorOffset)
                highlightedPostId = postId
                val result = snackbarHostState.showSnackbar(
                    message = "已跳到貼文 ${postId.takeLast(10)}",
                    actionLabel = "返回原位置",
                )
                if (result == SnackbarResult.ActionPerformed) {
                    listState.animateScrollToItem(returnIndex, returnOffset)
                }
            }
        }
    }
    val onReplyToClick =
        remember(viewModel) { { id: String -> viewModel.onReplyToClick(id) } }

    LaunchedEffect(readTrackingMode, thread?.posts) {
        if (readTrackingMode == ReadTrackingMode.THREAD_OPENED && thread != null) {
            viewModel.markAllPostsRead()
        }
    }

    LaunchedEffect(listState, displayedPosts, readTrackingMode) {
        if (readTrackingMode != ReadTrackingMode.POST_VISIBLE) return@LaunchedEffect
        val postIds = displayedPosts.mapTo(mutableSetOf()) { it.post.id }

        fun visiblePostIds(): Set<String> {
            val layoutInfo = listState.layoutInfo
            return layoutInfo.visibleItemsInfo.mapNotNullTo(mutableSetOf()) { itemInfo ->
                val postId = itemInfo.key as? String ?: return@mapNotNullTo null
                postId.takeIf {
                    it in postIds && visibleFraction(
                        itemOffset = itemInfo.offset,
                        itemSize = itemInfo.size,
                        viewportStartOffset = layoutInfo.viewportStartOffset,
                        viewportEndOffset = layoutInfo.viewportEndOffset,
                    ) >= 0.5f
                }
            }
        }

        snapshotFlow(::visiblePostIds).collectLatest { candidates ->
            if (candidates.isEmpty()) return@collectLatest
            delay(500)
            candidates.intersect(visiblePostIds()).forEach(viewModel::markPostRead)
        }
    }

    LaunchedEffect(authenticationRequiredNotice) {
        val sourceId = authenticationRequiredNotice ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "此看板需要登入，請到 Boards 頁面登入",
            actionLabel = "前往 Boards",
        )
        viewModel.consumeAuthenticationRequiredNotice(sourceId)
        if (result == SnackbarResult.ActionPerformed) onNavigateToBoards()
    }

    LaunchedEffect(thread?.posts, galleryRequest?.postId) {
        val request = galleryRequest ?: return@LaunchedEffect
        val currentThread = thread ?: return@LaunchedEffect
        if (currentThread.posts.none { it.id == request.postId }) {
            galleryRequest = null
        }
    }

    // Trigger screenshot capture when save is requested
    LaunchedEffect(isSavingScreenshots) {
        if (isSavingScreenshots) {
            val activity = context as? android.app.Activity ?: run {
                viewModel.onScreenshotsCaptured(emptyList())
                return@LaunchedEffect
            }
            val posts = thread?.posts ?: emptyList()
            val paths = capturePostsAsFiles(
                activity = activity,
                posts = posts,
                alwaysUseRawImage = alwaysUseRawImage,
                sourceId = viewModel.sourceKey,
                threadId = viewModel.threadId,
                resourceProvider = viewModel.resourceProvider,
            )
            viewModel.onScreenshotsCaptured(paths)
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .swipeToGoBack(onNavigateUp)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = thread?.title?.takeIf(String::isNotBlank) ?: "無題",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sourceBoardLabel.isNotBlank()) {
                                BodySmallText(
                                    text = sourceBoardLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        if (isSavingScreenshots) {
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.requestToggleSave() },
                                enabled = !isLoading,
                            ) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isSaved) "取消收藏" else "收藏貼文",
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.requestThreadWebLink(
                                    onReady = { handle ->
                                        val opened = openExternalLink(
                                            handle,
                                            viewModel.resourceProvider::consumeExternalLink,
                                            uriHandler::openUri,
                                        )
                                        if (!opened) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
                                            }
                                        }
                                    },
                                    onRejected = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
                                        }
                                    },
                                )
                            },
                            enabled = thread != null && !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in browser",
                            )
                        }
                        Box {
                            IconButton(onClick = { preferencesMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "閱讀顯示設定",
                                )
                            }
                            DropdownMenu(
                                expanded = preferencesMenuExpanded,
                                onDismissRequest = { preferencesMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("時間序＋脈絡跳轉") },
                                    leadingIcon = {
                                        if (replyDisplayMode == ReplyDisplayMode.CONTEXTUAL) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setReplyDisplayMode(ReplyDisplayMode.CONTEXTUAL)
                                        preferencesMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("遞迴縮排") },
                                    leadingIcon = {
                                        if (replyDisplayMode == ReplyDisplayMode.NESTED) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setReplyDisplayMode(ReplyDisplayMode.NESTED)
                                        preferencesMenuExpanded = false
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("滑過貼文才算已讀") },
                                    leadingIcon = {
                                        if (readTrackingMode == ReadTrackingMode.POST_VISIBLE) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setReadTrackingMode(ReadTrackingMode.POST_VISIBLE)
                                        preferencesMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("進入串就算已讀") },
                                    leadingIcon = {
                                        if (readTrackingMode == ReadTrackingMode.THREAD_OPENED) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        viewModel.setReadTrackingMode(ReadTrackingMode.THREAD_OPENED)
                                        preferencesMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                            .align(Alignment.TopCenter),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (loadError != null && thread != null) {
                            item(key = "refresh-error") {
                                ErrorNotice(message = loadError.orEmpty(), onRetry = viewModel::refresh)
                            }
                        }
                        items(displayedPosts, key = { it.post.id }) { threadedPost ->
                            val post = threadedPost.post
                            ExtPostCard(
                                post = post,
                                actualDepth = threadedPost.actualDepth,
                                visualDepth = threadedPost.visualDepth,
                                isOriginalPost = post.id == thread?.posts?.firstOrNull()?.id,
                                isRead = post.id in readPostIds,
                                isHighlighted = post.id == highlightedPostId,
                                onHighlightDone = {
                                    if (post.id == highlightedPostId) highlightedPostId = null
                                },
                                alwaysUseRawImage = alwaysUseRawImage,
                                commentUiState = commentStates[post.id],
                                onShowReplies = { repliesDialogForPostId = post.id },
                                onReplyToClick = onReplyToClick,
                                onMediaClick = { startIndex ->
                                    galleryRequest = GalleryRequest(post.id, startIndex)
                                },
                                onLoadMoreCommentsClick = { viewModel.loadMoreComments(post.id) },
                            )
                        }
                        if (thread != null) {
                            item(key = "thread-footer") {
                                ThreadPagingFooter(
                                    paging = threadPaging,
                                    isRefreshing = isLoading,
                                    onLoadMore = viewModel::loadMorePosts,
                                )
                            }
                        }
                    }

                    when {
                        thread == null && isLoading -> CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                        )
                        thread == null && loadError != null -> ErrorState(
                            message = loadError.orEmpty(),
                            onRetry = viewModel::refresh,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        galleryRequest?.let { request ->
            val post = thread?.posts?.firstOrNull { it.id == request.postId }
            if (post != null) {
                PostGallery(
                    post = post,
                    startIndex = request.startIndex,
                    isSaved = isSaved,
                    isSaving = isSavingScreenshots,
                    onToggleSave = { viewModel.requestToggleSave() },
                    onDismissRequest = { galleryRequest = null },
                    onReplyToClick = { targetId ->
                        galleryRequest = null
                        onReplyToClick(targetId)
                    },
                    onShowReplies = {
                        galleryRequest = null
                        repliesDialogForPostId = post.id
                    },
                )
            }
        }

        previewPost?.let { post ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPreview() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissPreview() }) { Text("關閉") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissPreview()
                            jumpToPost(post.id)
                        },
                    ) { Text("跳到原文") }
                },
                title = {
                    Column {
                        Text("引用貼文")
                        BodySmallText(
                            text = "${post.author ?: "未知作者"} · ${post.id}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                text = {
                    QuotePreviewContent(
                        post = post,
                        alwaysUseRawImage = alwaysUseRawImage,
                    )
                },
            )
        }

        repliesDialogForPostId?.let { postId ->
            val dialogReplies = remember(thread, postId) {
                thread!!.posts.filterRepliesBy(postId)
            }
            AlertDialog(
                onDismissRequest = { repliesDialogForPostId = null },
                confirmButton = {
                    TextButton(onClick = { repliesDialogForPostId = null }) { Text("關閉") }
                },
                title = { Text("回文清單 (${dialogReplies.size})") },
                text = {
                    LazyColumn {
                        items(dialogReplies, key = { it.id }) { reply ->
                            AppCard(
                                onClick = {
                                    repliesDialogForPostId = null
                                    jumpToPost(reply.id)
                                }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        reply.sourceIconUrl?.let {
                                            AsyncImage(
                                                model = resourceModelOrNull(it),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                        LabelMediumText(
                                            text = reply.author ?: "Unknown",
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        BodySmallText(reply.id.takeLast(10))
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    ParagraphsContent(
                                        paragraphs = reply.content,
                                        alwaysUseRawImage = alwaysUseRawImage,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                },
            )
        }
    }
}

private const val EXTERNAL_LINK_REJECTED_MESSAGE = "網站連結被安全政策阻擋或已失效"

@Composable
private fun ThreadPagingFooter(
    paging: ThreadPagingState,
    isRefreshing: Boolean,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            paging.isAppending -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            paging.appendError != null -> {
                Text(
                    text = paging.appendError,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onLoadMore) { Text("重試載入更多") }
            }
            paging.hasMore -> FilledTonalButton(
                onClick = onLoadMore,
                enabled = !isRefreshing,
            ) { Text("載入更多") }
            !isRefreshing -> BodySmallText("沒有更多資料")
        }
    }
}

@Composable
private fun ErrorNotice(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onRetry) { Text("重試") }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("重新載入") }
    }
}

@Composable
private fun QuotePreviewContent(post: Post, alwaysUseRawImage: Boolean) {
    val nonMediaParagraphs = remember(post.content) {
        post.content.filterNot { it is Paragraph.ImageInfo || it is Paragraph.VideoInfo }
    }
    val mediaModel = remember(post.content, alwaysUseRawImage) {
        post.content.firstNotNullOfOrNull { paragraph ->
            when (paragraph) {
                is Paragraph.ImageInfo -> if (alwaysUseRawImage) paragraph.raw else paragraph.thumb ?: paragraph.raw
                is Paragraph.VideoInfo -> paragraph.url
                else -> null
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ParagraphsContent(
            paragraphs = nonMediaParagraphs,
            alwaysUseRawImage = alwaysUseRawImage,
        )
        mediaModel?.let { model ->
            AsyncImage(
                model = resourceModelOrNull(model),
                contentDescription = "引用貼文媒體預覽",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ExtPostCard(
    post: Post,
    modifier: Modifier = Modifier,
    actualDepth: Int,
    visualDepth: Int,
    isOriginalPost: Boolean,
    isRead: Boolean,
    isHighlighted: Boolean,
    onHighlightDone: () -> Unit,
    alwaysUseRawImage: Boolean,
    commentUiState: CommentUiState?,
    onShowReplies: () -> Unit,
    onReplyToClick: (String) -> Unit,
    onMediaClick: (Int) -> Unit,
    onLoadMoreCommentsClick: () -> Unit,
) {
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightAlpha.snapTo(0.35f)
            highlightAlpha.animateTo(0f, animationSpec = tween(durationMillis = HIGHLIGHT_DURATION_MS))
            onHighlightDone()
        }
    }

    val visibleComments = commentUiState?.visibleComments.orEmpty()
    PostBlock(
        modifier = modifier,
        visualDepth = visualDepth,
        showGuides = !isOriginalPost,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PostCard(
                post = post,
                isOriginalPost = isOriginalPost,
                isRead = isRead,
                actualDepth = actualDepth,
                highlightAlpha = highlightAlpha.value,
                alwaysUseRawImage = alwaysUseRawImage,
                onShowReplies = onShowReplies,
                onReplyToClick = onReplyToClick,
                onMediaClick = onMediaClick,
            )

            visibleComments.forEach { comment ->
                CommentItem(comment = comment, alwaysUseRawImage = alwaysUseRawImage)
            }
            when {
                commentUiState?.isLoading == true ->
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 4.dp),
                        strokeWidth = 2.dp,
                    )

                commentUiState?.hasMore == true ->
                    TextButton(
                        onClick = onLoadMoreCommentsClick,
                        contentPadding = PaddingValues(0.dp),
                    ) { LabelSmallText(text = "載入更多留言") }
            }
        }
    }

}

/**
 * Leaves a compact, always-visible branch gutter outside of the post surface. The content stays
 * readable at deep nesting levels, while the retained actual depth is shown by [PostCard].
 */
@Composable
private fun PostBlock(
    visualDepth: Int,
    showGuides: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gutter = 20.dp
    val outline = MaterialTheme.colorScheme.outlineVariant
    val currentBranch = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (!showGuides || visualDepth == 0) return@drawBehind

                val gutterPx = gutter.toPx()
                val elbowY = 22.dp.toPx().coerceAtMost(size.height)
                repeat(visualDepth) { level ->
                    val guideX = gutterPx * (level + 0.5f)
                    drawLine(
                        color = if (level == visualDepth - 1) currentBranch else outline,
                        start = Offset(guideX, 0f),
                        end = Offset(guideX, size.height),
                        strokeWidth = if (level == visualDepth - 1) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }
                val currentGuideX = gutterPx * (visualDepth - 0.5f)
                drawLine(
                    color = currentBranch,
                    start = Offset(currentGuideX, elbowY),
                    end = Offset(gutterPx * visualDepth, elbowY),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    ) {
        Box(modifier = Modifier.padding(start = gutter * visualDepth)) {
            content()
        }
    }
}

@Composable
internal fun PostCard(
    post: Post,
    modifier: Modifier = Modifier,
    isOriginalPost: Boolean = false,
    isRead: Boolean = true,
    actualDepth: Int = 0,
    highlightAlpha: Float,
    alwaysUseRawImage: Boolean,
    onShowReplies: () -> Unit,
    onReplyToClick: (String) -> Unit,
    onMediaClick: (index: Int) -> Unit,
) {
    val postState = buildString {
        append(
            when {
                isOriginalPost -> "原始貼文"
                actualDepth > 0 -> "第${actualDepth}層回覆"
                else -> "回覆貼文"
            },
        )
        if (!isRead) append("，未讀")
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = postState },
        shape = MaterialTheme.shapes.large,
        color = if (isOriginalPost) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
        if (isOriginalPost || !isRead) {
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                post.sourceIconUrl?.let {
                    AsyncImage(
                        model = resourceModelOrNull(it),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    LabelMediumText(
                        text = post.author ?: "Unknown",
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        post.createdAt?.let {
                            BodySmallText(
                                text = android.text.format.DateUtils.getRelativeTimeSpanString(it)
                                    .toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BodySmallText(
                            text = "#${post.id.takeLast(10)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isOriginalPost) {
                        PostStatusBadge("OP")
                    }
                    if (!isRead) {
                        PostStatusBadge("未讀", showDot = true)
                    }
                    if (!isOriginalPost && actualDepth > 3) {
                        PostStatusBadge("${actualDepth}+")
                    } else if (!isOriginalPost && actualDepth > 0) {
                        PostStatusBadge("第${actualDepth}層")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    post.replyCount?.let {
                        TextButton(
                            onClick = onShowReplies,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = "查看 $it 則回覆" },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            BodySmallText("$it")
                        }
                    }
                    post.comments.size.takeIf { it > 0 }?.let {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BodySmallText("$it")
                    }
                }
            }

            ParagraphsContent(
                paragraphs = post.content,
                alwaysUseRawImage = alwaysUseRawImage,
                onReplyToClick = onReplyToClick,
                onMediaClick = onMediaClick,
            )
        }
        if (highlightAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha))
            )
        }
        }
    }
}

@Composable
private fun PostStatusBadge(text: String, showDot: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            LabelSmallText(text = text, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, alwaysUseRawImage: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // 頭像佔位
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            comment.author?.let {
                LabelMediumText(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ParagraphsContent(
                paragraphs = comment.content,
                alwaysUseRawImage = alwaysUseRawImage,
                useSmallText = true,
            )
        }
    }
}

@Composable
private fun ParagraphsContent(
    paragraphs: List<Paragraph>,
    alwaysUseRawImage: Boolean,
    onReplyToClick: ((String) -> Unit)? = null,
    onMediaClick: ((index: Int) -> Unit)? = null,
    useSmallText: Boolean = false,
) {
    var mediaIndex = 0
    paragraphs.forEach { paragraph ->
        when (paragraph) {
            is Paragraph.Text -> if (useSmallText) paragraph.Small() else paragraph.View()
            is Paragraph.RichText -> if (useSmallText) paragraph.Small() else paragraph.View()
            is Paragraph.Quote -> paragraph.Small()
            is Paragraph.ReplyTo -> if (useSmallText) paragraph.Small() else paragraph.View(onReplyToClick)
            is Paragraph.Link -> if (useSmallText) paragraph.Small() else paragraph.View()
            is Paragraph.ImageInfo -> {
                val index = mediaIndex++
                paragraph.View(alwaysUseRawImage, onClick = onMediaClick?.let { cb -> { cb(index) } })
            }
            is Paragraph.VideoInfo -> {
                val index = mediaIndex++
                paragraph.View(onClick = onMediaClick?.let { cb -> { cb(index) } })
            }
        }
    }
}
