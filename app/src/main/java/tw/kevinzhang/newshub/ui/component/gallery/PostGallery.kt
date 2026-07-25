package tw.kevinzhang.newshub.ui.component.gallery

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.ui.component.Small
import tw.kevinzhang.newshub.ui.component.View

private val MediaCanvas = Color(0xFF08070A)
private const val GalleryLayoutAnimationMillis = 260
private const val PanelVelocityThresholdDp = 125

/** The panel takes layout space; it is deliberately not a modal bottom sheet. */
internal enum class GalleryPanelState {
    Immersive,
    Expanded,
}

internal fun GalleryPanelState.onMediaTap(): GalleryPanelState = when (this) {
    GalleryPanelState.Immersive -> GalleryPanelState.Expanded
    GalleryPanelState.Expanded -> GalleryPanelState.Immersive
}

internal fun galleryPanelVisibleFraction(
    offset: Float,
    dragRange: Float,
    fallbackState: GalleryPanelState,
): Float {
    if (!offset.isFinite() || !dragRange.isFinite() || dragRange <= 0f) {
        return if (fallbackState == GalleryPanelState.Expanded) 1f else 0f
    }
    return (1f - offset / dragRange).coerceIn(0f, 1f)
}

internal fun galleryMediaItems(paragraphs: List<Paragraph>): List<Paragraph> =
    paragraphs.filter { it is Paragraph.ImageInfo || it is Paragraph.VideoInfo }

internal fun galleryInitialPage(startIndex: Int, itemCount: Int): Int =
    if (itemCount <= 0) 0 else startIndex.coerceIn(0, itemCount - 1)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostGallery(
    post: Post,
    startIndex: Int = 0,
    isSaved: Boolean = false,
    isSaving: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onReplyToClick: ((String) -> Unit)? = null,
    onShowReplies: (() -> Unit)? = null,
) {
    val mediaItems = remember(post.content) { galleryMediaItems(post.content) }
    val nonMediaParagraphs = remember(post.content) {
        post.content.filterNot { it is Paragraph.ImageInfo || it is Paragraph.VideoInfo }
    }

    if (mediaItems.isEmpty()) {
        LaunchedEffect(Unit) { onDismissRequest() }
        return
    }

    val initialPage = galleryInitialPage(startIndex, mediaItems.size)
    val pagerState = rememberPagerState(initialPage = initialPage) { mediaItems.size }
    val thumbnailState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelDragState = remember(density) {
        AnchoredDraggableState(
            initialValue = GalleryPanelState.Expanded,
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { PanelVelocityThresholdDp.dp.toPx() } },
            snapAnimationSpec = tween(GalleryLayoutAnimationMillis),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        if (mediaItems.size > 1) thumbnailState.animateScrollToItem(pagerState.currentPage)
    }

    Dialog(
        onDismissRequest = {
            val isFullyImmersive =
                panelDragState.settledValue == GalleryPanelState.Immersive &&
                    panelDragState.targetValue == GalleryPanelState.Immersive &&
                    !panelDragState.isAnimationRunning
            if (!isFullyImmersive) {
                coroutineScope.launch {
                    panelDragState.animateTo(GalleryPanelState.Immersive)
                }
            } else {
                onDismissRequest()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MediaCanvas),
        ) {
            val useTwoPanes = maxWidth >= 600.dp || maxWidth > maxHeight
            val portraitExpandedPanelHeight = maxHeight * 0.40f
            val wideExpandedPanelWidth = minOf(420.dp, maxOf(300.dp, maxWidth * 0.40f))
            val panelDragRange = with(density) {
                if (useTwoPanes) {
                    wideExpandedPanelWidth.toPx()
                } else {
                    portraitExpandedPanelHeight.toPx()
                }
            }
            SideEffect {
                panelDragState.updateAnchors(
                    DraggableAnchors {
                        GalleryPanelState.Expanded at 0f
                        GalleryPanelState.Immersive at panelDragRange
                    },
                )
            }
            val panelVisibleFraction = galleryPanelVisibleFraction(
                offset = panelDragState.offset,
                dragRange = panelDragRange,
                fallbackState = panelDragState.currentValue,
            )
            val panelVisualState = if (panelVisibleFraction > 0.5f) {
                GalleryPanelState.Expanded
            } else {
                GalleryPanelState.Immersive
            }
            val panelVisible = panelVisibleFraction > 0f
            val currentItemIsVideo = mediaItems[pagerState.currentPage] is Paragraph.VideoInfo
            val topControlsVisible = panelVisible || currentItemIsVideo
            val portraitPanelHeight = portraitExpandedPanelHeight * panelVisibleFraction
            val widePanelWidth = wideExpandedPanelWidth * panelVisibleFraction
            val togglePanel: () -> Unit = {
                val target = panelVisualState.onMediaTap()
                coroutineScope.launch { panelDragState.animateTo(target) }
            }

            val topControls: @Composable () -> Unit = {
                AnimatedVisibility(
                    visible = topControlsVisible,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    GalleryTopControls(
                        currentPage = pagerState.currentPage,
                        pageCount = mediaItems.size,
                        panelState = panelVisualState,
                        isSaved = isSaved,
                        isSaving = isSaving,
                        onTogglePanel = togglePanel,
                        onToggleSave = onToggleSave,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
            val mediaPager: @Composable (Modifier) -> Unit = { modifier ->
                GalleryMediaPager(
                    mediaItems = mediaItems,
                    pagerState = pagerState,
                    isZoomed = isZoomed,
                    onZoomedChange = { isZoomed = it },
                    onMediaTap = togglePanel,
                    modifier = modifier,
                )
            }
            val thumbnailRail: @Composable () -> Unit = {
                if (mediaItems.size > 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp * panelVisibleFraction)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        GalleryThumbnailRail(
                            mediaItems = mediaItems,
                            currentPage = pagerState.currentPage,
                            thumbnailState = thumbnailState,
                            onThumbnailClick = { page ->
                                coroutineScope.launch { pagerState.animateScrollToPage(page) }
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }

            if (useTwoPanes) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        topControls()
                        mediaPager(Modifier.weight(1f))
                        thumbnailRail()
                    }
                    GallerySupportingPanel(
                        dragState = panelDragState,
                        dragEnabled = false,
                        panelVisibleFraction = panelVisibleFraction,
                        post = post,
                        nonMediaParagraphs = nonMediaParagraphs,
                        onDismissRequest = onDismissRequest,
                        onReplyToClick = onReplyToClick,
                        onShowReplies = onShowReplies,
                        shape = RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                        ),
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(widePanelWidth)
                            .clipToBounds(),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    topControls()
                    mediaPager(Modifier.weight(1f))
                    thumbnailRail()
                    GallerySupportingPanel(
                        dragState = panelDragState,
                        dragEnabled = true,
                        panelVisibleFraction = panelVisibleFraction,
                        post = post,
                        nonMediaParagraphs = nonMediaParagraphs,
                        onDismissRequest = onDismissRequest,
                        onReplyToClick = onReplyToClick,
                        onShowReplies = onShowReplies,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(portraitPanelHeight)
                            .clipToBounds(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryMediaPager(
    mediaItems: List<Paragraph>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isZoomed: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    onMediaTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
    ) { page ->
        when (val item = mediaItems[page]) {
            is Paragraph.ImageInfo -> {
                var loaded by remember(item.raw) { mutableStateOf(false) }
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { if (!isZoomed) onMediaTap() },
                        ),
                ) {
                    ZoomableBox(
                        loaded = loaded,
                        onZoomedChange = onZoomedChange,
                    ) {
                        AsyncImage(
                            model = item.raw,
                            contentDescription = "媒體 ${page + 1}，共 ${mediaItems.size} 個",
                            onSuccess = { loaded = true },
                        )
                    }
                }
            }

            is Paragraph.VideoInfo -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (item.site == Paragraph.VideoInfo.Site.YOUTUBE) {
                    extractYouTubeVideoId(item.url)?.let { YouTubePlayer(videoId = it) }
                } else {
                    VideoPlayer(url = item.url)
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun GalleryTopControls(
    currentPage: Int,
    pageCount: Int,
    panelState: GalleryPanelState,
    isSaved: Boolean,
    isSaving: Boolean,
    onTogglePanel: () -> Unit,
    onToggleSave: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        GalleryIconButton(onClick = onDismissRequest, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Default.Close, contentDescription = "關閉媒體檢視器", tint = Color.White)
        }

        if (pageCount > 1) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f),
                contentColor = Color.White,
            ) {
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GalleryIconButton(
                onClick = onTogglePanel,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = if (panelState == GalleryPanelState.Immersive) {
                        "顯示貼文資訊"
                    } else {
                        "隱藏貼文資訊"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            onToggleSave?.let { toggleSave ->
                GalleryIconButton(
                    onClick = toggleSave,
                    enabled = !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (isSaved) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = if (isSaved) "取消收藏討論串" else "收藏討論串",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GallerySupportingPanel(
    dragState: AnchoredDraggableState<GalleryPanelState>,
    dragEnabled: Boolean,
    panelVisibleFraction: Float,
    post: Post,
    nonMediaParagraphs: List<Paragraph>,
    onDismissRequest: () -> Unit,
    onReplyToClick: ((String) -> Unit)?,
    onShowReplies: (() -> Unit)?,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            GalleryPanelHandle(
                dragState = dragState,
                dragEnabled = dragEnabled,
                panelVisibleFraction = panelVisibleFraction,
            )
            GalleryExpandedContent(
                post = post,
                nonMediaParagraphs = nonMediaParagraphs,
                onDismissRequest = onDismissRequest,
                onReplyToClick = onReplyToClick,
                onShowReplies = onShowReplies,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun GalleryPanelHandle(
    dragState: AnchoredDraggableState<GalleryPanelState>,
    dragEnabled: Boolean,
    panelVisibleFraction: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .anchoredDraggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = dragEnabled,
            )
            .semantics {
                contentDescription = "上下拖曳貼文資訊"
                stateDescription = when {
                    panelVisibleFraction <= 0f -> "已隱藏"
                    panelVisibleFraction >= 1f -> "已展開"
                    else -> "拖曳中"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

@Composable
private fun GalleryExpandedContent(
    post: Post,
    nonMediaParagraphs: List<Paragraph>,
    onDismissRequest: () -> Unit,
    onReplyToClick: ((String) -> Unit)?,
    onShowReplies: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
    ) {
        PostHeader(post = post)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            nonMediaParagraphs.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> paragraph.View()
                    is Paragraph.RichText -> paragraph.View()
                    is Paragraph.Quote -> paragraph.Small()
                    is Paragraph.ReplyTo -> paragraph.Small(onReplyToClick)
                    is Paragraph.Link -> paragraph.View()
                    is Paragraph.ImageInfo, is Paragraph.VideoInfo -> Unit
                }
            }
        }
        GalleryPanelActions(
            onDismissRequest = onDismissRequest,
            onShowReplies = onShowReplies,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun GalleryPanelActions(
    onDismissRequest: () -> Unit,
    onShowReplies: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onDismissRequest,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("回到原文")
        }
        onShowReplies?.let { showReplies ->
            TextButton(
                onClick = {
                    onDismissRequest()
                    showReplies()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("回覆")
            }
        }
    }
}

@Composable
private fun GalleryThumbnailRail(
    mediaItems: List<Paragraph>,
    currentPage: Int,
    thumbnailState: LazyListState,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = thumbnailState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items = mediaItems, key = { index, _ -> index }) { index, item ->
            val isSelected = index == currentPage
            Surface(
                onClick = { onThumbnailClick(index) },
                modifier = Modifier
                    .size(width = 72.dp, height = 54.dp)
                    .semantics {
                        selected = isSelected
                        contentDescription = "媒體縮圖 ${index + 1}，共 ${mediaItems.size} 個"
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                when (item) {
                    is Paragraph.ImageInfo -> AsyncImage(
                        model = item.thumb ?: item.raw,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    is Paragraph.VideoInfo -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun PostHeader(post: Post, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        post.sourceIconUrl?.let { iconUrl ->
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = post.author?.takeIf(String::isNotBlank) ?: "未知作者",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = postMetadata(post),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun postMetadata(post: Post): String {
    val time = post.createdAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() }
    val id = "#${post.id.takeLast(10)}"
    return listOfNotNull(time, id).joinToString(" · ")
}
