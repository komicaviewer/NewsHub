package tw.kevinzhang.newshub.ui.thread

import android.animation.ObjectAnimator
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.component.gallery.LazyGallery
import kotlin.math.roundToInt

private val WEBVIEW_TEXT_ZOOM_STEPS = listOf(75, 100, 125, 150, 175, 200)

/**
 * Wraps a raw HTML content fragment into a complete HTML document suitable for WebView.
 * - viewport meta ensures the page fits device width (not 980 px desktop default)
 * - CSS constrains images to viewport width, which the fragment itself cannot express
 */
private fun String.asWebViewDocument(): String = """
    <!DOCTYPE html>
    <html><head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>img { max-width: 100%; height: auto; }</style>
    </head><body>$this</body></html>
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenWebClick: (url: String) -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel(),
) {
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val threadUrl by viewModel.threadUrl.collectAsStateWithLifecycle()
    val previewPost by viewModel.previewPost.collectAsStateWithLifecycle()
    val commentStates by viewModel.commentStates.collectAsStateWithLifecycle()
    val alwaysUseRawImage by viewModel.alwaysUseRawImage.collectAsStateWithLifecycle()
    val useWebViewPosts by viewModel.useWebViewPosts.collectAsStateWithLifecycle()
    val webViewTextZoom by viewModel.webViewTextZoom.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value > size.width * 0.2f) {
                                offsetX.animateTo(
                                    targetValue = size.width.toFloat(),
                                    animationSpec = tween(durationMillis = 200),
                                )
                                onNavigateUp()
                            } else {
                                offsetX.animateTo(0f, animationSpec = spring())
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                        }
                    },
                )
            },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(thread?.title ?: "") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        if (threadUrl != null) {
                            IconButton(onClick = { onOpenWebClick(threadUrl!!) }) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open in browser",
                                )
                            }
                        }
                    }
                )
            },
        ) { padding ->
            if (thread == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                val onReplyToClick =
                    remember(viewModel) { { id: String -> viewModel.onReplyToClick(id) } }
                val onZoomChange =
                    remember(viewModel) { { zoom: Int -> viewModel.setWebViewTextZoom(zoom) } }
                LazyColumn(modifier = Modifier.padding(padding)) {
                    items(thread!!.posts, key = { it.id }) { post ->
                        ExtPostCard(
                            post = post,
                            useWebView = post.id in useWebViewPosts,
                            onEnableWebView = { viewModel.enableWebViewForPost(post.id) },
                            alwaysUseRawImage = alwaysUseRawImage,
                            commentUiState = commentStates[post.id],
                            onReplyToClick = onReplyToClick,
                            onLoadMoreCommentsClick = { viewModel.loadMoreComments(post.id) },
                            textZoom = webViewTextZoom,
                            onZoomChange = onZoomChange,
                        )
                    }
                }
            }
        }

        previewPost?.let { post ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissPreview() },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissPreview() }) { Text("Close") }
                },
                title = { Text("Post ${post.id}") },
                text = {
                    Column {
                        post.content.forEach { paragraph ->
                            when (paragraph) {
                                is Paragraph.Text -> Text(paragraph.content)
                                is Paragraph.Quote -> Text("> ${paragraph.content}")
                                is Paragraph.ReplyTo -> Text(">> ${paragraph.id}")
                                is Paragraph.Link -> Text(paragraph.content)
                                is Paragraph.ImageInfo -> {
                                    val url =
                                        if (alwaysUseRawImage) paragraph.raw else paragraph.thumb
                                    url?.let { AsyncImage(model = it, contentDescription = null) }
                                }

                                is Paragraph.VideoInfo -> Text("[video]")
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ExtPostCard(
    post: Post,
    useWebView: Boolean,
    onEnableWebView: () -> Unit,
    alwaysUseRawImage: Boolean,
    commentUiState: CommentUiState?,
    onReplyToClick: (String) -> Unit,
    onLoadMoreCommentsClick: () -> Unit,
    textZoom: Int,
    onZoomChange: (Int) -> Unit,
) {
    val rawImages = remember(post.id) {
        post.content.filterIsInstance<Paragraph.ImageInfo>().map { it.raw }
    }
    var galleryStartIndex by remember { mutableStateOf<Int?>(null) }

    AppCard {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = "Post ${post.id}", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(4.dp))
            val rawHtml = post.rawHtml
            when {
                useWebView && rawHtml != null -> {
                    PostWebView(
                        rawHtml = rawHtml,
                        textZoom = textZoom,
                        onZoomChange = onZoomChange,
                    )
                }
                !useWebView && post.content.isEmpty() && rawHtml != null -> {
                    FilledTonalButton(
                        onClick = onEnableWebView,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                    ) {
                        Text("使用WebView引擎渲染")
                    }
                }
                else -> {
                    var imageIndex = 0
                    post.content.forEach { paragraph ->
                        when (paragraph) {
                            is Paragraph.Text -> Text(paragraph.content)
                            is Paragraph.Quote -> Text(
                                "> ${paragraph.content}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            is Paragraph.ReplyTo -> TextButton(
                                onClick = { onReplyToClick(paragraph.id) },
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(">> ${paragraph.id}") }
                            is Paragraph.Link -> Text(
                                paragraph.content,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            is Paragraph.ImageInfo -> {
                                val index = imageIndex++
                                val url = if (alwaysUseRawImage) paragraph.raw else paragraph.thumb
                                url?.let {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { galleryStartIndex = index },
                                    )
                                }
                            }
                            is Paragraph.VideoInfo -> Text("[video: ${paragraph.url}]")
                        }
                    }
                }
            }
        }
    }
    val visibleComments = commentUiState?.visibleComments.orEmpty()
    if (visibleComments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        visibleComments.forEach { comment ->
            CommentItem(comment = comment, alwaysUseRawImage = alwaysUseRawImage)
        }
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
            ) { Text("載入更多留言", style = MaterialTheme.typography.labelSmall) }
    }
    Spacer(modifier = Modifier.height(8.dp))

    galleryStartIndex?.let { startIndex ->
        LazyGallery(
            images = rawImages,
            startIndex = startIndex,
            onDismissRequest = { galleryStartIndex = null },
        )
    }
}

@Composable
private fun PostWebView(rawHtml: String, textZoom: Int, onZoomChange: (Int) -> Unit) {
    val webViewRef = remember { arrayOfNulls<WebView>(1) }
    var canScrollUp by remember { mutableStateOf(false) }
    var canScrollDown by remember { mutableStateOf(false) }

    fun updateScrollState(wv: WebView) {
        canScrollUp = wv.canScrollVertically(-1)
        canScrollDown = wv.canScrollVertically(1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp),
    ) {
        AndroidView(
            factory = { context ->
                object : WebView(context) {
                    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
                        super.onScrollChanged(l, t, oldl, oldt)
                        updateScrollState(this)
                    }
                }.apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // onPageFinished fires when the DOM is fetched, but WebView's
                            // renderer process fills in contentHeight asynchronously.
                            // canScrollVertically() returns false until contentHeight is ready,
                            // so we poll at 100/300/700 ms to catch the first settled value.
                            // Trade-off: timing is heuristic — slow devices may still miss the
                            // 700 ms window; very fast devices run two redundant checks.
                            view.postDelayed({ updateScrollState(view) }, 100)
                            view.postDelayed({ updateScrollState(view) }, 300)
                            view.postDelayed({ updateScrollState(view) }, 700)
                        }
                    }
                    webViewRef[0] = this
                }
            },
            update = { wv ->
                wv.settings.textZoom = textZoom
                if (wv.tag != rawHtml) {
                    wv.tag = rawHtml
                    wv.loadDataWithBaseURL(
                        "https://forum.gamer.com.tw",
                        rawHtml.asWebViewDocument(),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                } else {
                    // textZoom changed — re-check after layout settles
                    wv.post { updateScrollState(wv) }
                }
            },
            onRelease = { wv ->
                webViewRef[0] = null
                wv.destroy()
            },
            modifier = Modifier.fillMaxSize(),
        )

        WebViewControls(
            textZoom = textZoom,
            onZoomChange = onZoomChange,
            canScrollUp = canScrollUp,
            canScrollDown = canScrollDown,
            onScrollUp = {
                webViewRef[0]?.let { wv ->
                    val target = (wv.scrollY - 300).coerceAtLeast(0)
                    ObjectAnimator.ofInt(wv, "scrollY", wv.scrollY, target)
                        .apply { duration = 250; start() }
                }
            },
            onScrollDown = {
                webViewRef[0]?.let { wv ->
                    ObjectAnimator.ofInt(wv, "scrollY", wv.scrollY, wv.scrollY + 300)
                        .apply { duration = 250; start() }
                }
            },
            onRefresh = {
                onZoomChange(100)
                webViewRef[0]?.let { wv ->
                    wv.loadDataWithBaseURL(
                        "https://forum.gamer.com.tw",
                        rawHtml.asWebViewDocument(),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    // keep tag in sync so the update block doesn't double-load
                    wv.tag = rawHtml
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun WebViewControls(
    textZoom: Int,
    onZoomChange: (Int) -> Unit,
    canScrollUp: Boolean,
    canScrollDown: Boolean,
    onScrollUp: () -> Unit,
    onScrollDown: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = WEBVIEW_TEXT_ZOOM_STEPS.indexOf(textZoom).takeIf { it >= 0 }
        ?: WEBVIEW_TEXT_ZOOM_STEPS.indexOf(100)
    val buttonSize = 48.dp

    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(modifier = Modifier.size(buttonSize))
            if (canScrollUp) {
                FilledTonalIconButton(onClick = onScrollUp) { Text("↑") }
            } else {
                Spacer(modifier = Modifier.size(buttonSize))
            }
            Spacer(modifier = Modifier.size(buttonSize))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledTonalIconButton(
                onClick = { onZoomChange(WEBVIEW_TEXT_ZOOM_STEPS[currentIndex + 1]) },
                enabled = currentIndex < WEBVIEW_TEXT_ZOOM_STEPS.lastIndex,
            ) { Text("A+") }
            FilledTonalIconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(18.dp),
                )
            }
            FilledTonalIconButton(
                onClick = { onZoomChange(WEBVIEW_TEXT_ZOOM_STEPS[currentIndex - 1]) },
                enabled = currentIndex > 0,
            ) { Text("A-") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(modifier = Modifier.size(buttonSize))
            if (canScrollDown) {
                FilledTonalIconButton(onClick = onScrollDown) { Text("↓") }
            } else {
                Spacer(modifier = Modifier.size(buttonSize))
            }
            Spacer(modifier = Modifier.size(buttonSize))
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
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            comment.content.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> Text(paragraph.content, style = MaterialTheme.typography.bodySmall)
                    is Paragraph.Quote -> Text("> ${paragraph.content}", style = MaterialTheme.typography.bodySmall)
                    is Paragraph.ReplyTo -> Text(">> ${paragraph.id}", style = MaterialTheme.typography.bodySmall)
                    is Paragraph.Link -> Text(paragraph.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    is Paragraph.ImageInfo -> {
                        val url = if (alwaysUseRawImage) paragraph.raw else paragraph.thumb
                        url?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth()) }
                    }
                    is Paragraph.VideoInfo -> Text("[video]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
