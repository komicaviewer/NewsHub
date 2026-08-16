package tw.kevinzhang.newshub.ui.collection

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard
import tw.kevinzhang.newshub.ui.component.resourceModelOrNull

private const val BAR_VISIBILITY_ANIMATION_MILLIS = 220
private val BarVisibilityScrollThreshold = 12.dp

/**
 * UI-element state owned by one collection destination. It deliberately keeps scrolling and
 * bar visibility out of the app shell: a bottom-tab reselect is an immediate action, not state
 * which can be replayed after navigating back from a thread.
 */
@Stable
class CollectionTimelineState internal constructor(
    val listState: LazyListState,
    private val barsVisibleState: MutableState<Boolean>,
) {
    var barsVisible by barsVisibleState
        private set

    fun showBars() {
        barsVisible = true
    }

    fun updateBarsVisibility(visible: Boolean) {
        barsVisible = visible
    }

    suspend fun scrollToTop() {
        showBars()
        listState.animateScrollToItem(0)
    }
}

/**
 * Retains a separate saveable lazy-list position for every collection destination and activity
 * recreation. The collection key prevents one collection from inheriting another's UI state.
 */
@Composable
fun rememberCollectionTimelineState(collectionId: String): CollectionTimelineState =
    key(collectionId) {
        val listState = rememberLazyListState()
        val barsVisible = rememberSaveable { mutableStateOf(true) }
        remember(listState, barsVisible) { CollectionTimelineState(listState, barsVisible) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    timelineState: CollectionTimelineState,
    onOpenDrawer: () -> Unit,
    onThreadClick: (sourceKey: String, ThreadSummary, boardName: String?) -> Unit,
    onNavigateToBoardPicker: () -> Unit,
    onNavigateToBoards: () -> Unit,
    bottomOverlayHeight: Dp = 0.dp,
    viewModel: CollectionTimelineViewModel = hiltViewModel(),
) {
    val items = viewModel.timelinePager.collectAsLazyPagingItems()
    val collectionName by viewModel.collectionName.collectAsStateWithLifecycle()
    val rawImageSourceIds by viewModel.rawImageSourceIds.collectAsStateWithLifecycle()
    val sourceIconUrls: Map<String, String?> by viewModel.sourceIconUrls.collectAsStateWithLifecycle()
    val sourceNames by viewModel.sourceNames.collectAsStateWithLifecycle()
    val timelineDisplayMode by viewModel.timelineDisplayMode.collectAsStateWithLifecycle()
    val readThreadKeys by viewModel.readThreadKeys.collectAsStateWithLifecycle()
    val sourceLoadFailures by viewModel.sourceLoadFailures.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val availableSourceIds by viewModel.availableSourceIds.collectAsStateWithLifecycle()
    val selectedSourceId by viewModel.selectedSourceId.collectAsStateWithLifecycle()
    val authenticationRequiredNotice by viewModel.authenticationRequiredNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()

    val coroutineScope = rememberCoroutineScope()
    var topOverlayHeightPx by remember { mutableIntStateOf(0) }
    val topOverlayHeight = with(LocalDensity.current) { topOverlayHeightPx.toDp() }
    val timelineContentPadding = PaddingValues(
        start = 16.dp,
        top = topOverlayHeight + 8.dp,
        end = 16.dp,
        bottom = bottomOverlayHeight + 8.dp,
    )
    val activity = LocalContext.current as Activity
    val barsScrollConnection = rememberBarsVisibilityScrollConnection(
        onBarsVisibilityChange = timelineState::updateBarsVisibility,
    )
    val onSourceSelect = remember(
        coroutineScope,
        timelineState,
        selectedSourceId,
        viewModel,
    ) {
        { sourceId: String? ->
            if (shouldResetTimelinePosition(selectedSourceId, sourceId)) {
                timelineState.showBars()
                coroutineScope.launch {
                    timelineState.listState.scrollToItem(0)
                }
                viewModel.selectSource(sourceId)
            }
        }
    }

    LaunchedEffect(timelineState.listState) {
        snapshotFlow {
            timelineState.listState.firstVisibleItemIndex == 0 &&
                timelineState.listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { isAtTop ->
                if (isAtTop) timelineState.showBars()
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

    BackHandler { activity.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(barsScrollConnection),
    ) {
        PullToRefreshBox(
            isRefreshing = items.loadState.refresh is LoadState.Loading,
            onRefresh = {
                viewModel.clearSourceLoadFailures()
                items.refresh()
            },
            modifier = Modifier.fillMaxSize(),
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = items.loadState.refresh is LoadState.Loading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topOverlayHeight),
                )
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = timelineState.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = timelineContentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sourceLoadFailures.isNotEmpty()) {
                        item(key = "source-load-failures") {
                            SourceFailureNotice(
                                failures = sourceLoadFailures,
                                onRetry = {
                                    viewModel.clearSourceLoadFailures()
                                    items.refresh()
                                },
                            )
                        }
                    }
                    items(
                        count = items.itemCount,
                    ) { index ->
                        val summary = items[index] ?: return@items
                        val subscriptionRecord = subscriptions?.firstOrNull {
                            it.sourceIdentity.sourceId == summary.sourceId &&
                                it.subscription.boardUrl == summary.boardUrl
                        }
                        val subscription = subscriptionRecord?.subscription
                        ThreadSummaryCard(
                            summary = summary,
                            alwaysUseRawImage = summary.sourceId in rawImageSourceIds,
                            sourceIconUrl = sourceIconUrls[summary.sourceId],
                            sourceName = sourceNames[summary.sourceId],
                            boardName = subscription?.boardName,
                            displayMode = timelineDisplayMode,
                            isRead = subscription?.let { (it.sourceKey to summary.id) in readThreadKeys } == true,
                            onClick = {
                                subscription?.let {
                                    onThreadClick(it.sourceKey, summary, it.boardName)
                                }
                            },
                        )
                    }
                    when (val appendState = items.loadState.append) {
                        is LoadState.Error -> {
                            item(key = "append-error") {
                                LaunchedEffect(appendState.error) {
                                    Log.e("CollectionTimeline", "Append load failed", appendState.error)
                                }
                                AppendLoadFooter(onRetry = items::retry)
                            }
                        }

                        is LoadState.Loading -> {
                            item(key = "append-loading") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Box(
                                        modifier = Modifier.padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

            when (val refreshState = items.loadState.refresh) {
                is LoadState.Error -> {
                    LaunchedEffect(refreshState.error) {
                        Log.e("CollectionTimeline", "Refresh failed", refreshState.error)
                    }
                    if (items.itemCount == 0) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("無法載入時間軸")
                            Button(onClick = {
                                viewModel.clearSourceLoadFailures()
                                items.refresh()
                            }) {
                                Text("重新整理")
                            }
                        }
                    }
                }

                else -> {}
            }

            val refreshSettledSuccessfully = items.loadState.refresh.let {
                it !is LoadState.Loading && it !is LoadState.Error
            }
            if (subscriptions?.isEmpty() == true && refreshSettledSuccessfully) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BodyLargeText(
                        text = "尚未加入任何 Board",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onNavigateToBoardPicker) {
                        Text("新增 Board")
                    }
                }
            } else if (
                selectedSourceId != null &&
                subscriptions?.isNotEmpty() == true &&
                items.itemCount == 0 &&
                refreshSettledSuccessfully
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BodyLargeText(
                        text = "${sourceNames[selectedSourceId] ?: selectedSourceId} 目前沒有貼文",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { onSourceSelect(null) }) {
                        Text("顯示全部")
                    }
                }
            }

        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomOverlayHeight + 8.dp),
        )

        AnimatedVisibility(
            visible = timelineState.barsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    // Keep the last complete overlay size while it is hidden, so toggling the
                    // bars only changes drawing and never repositions the list viewport.
                    if (size.height > 0) {
                        topOverlayHeightPx = size.height
                    }
                },
            enter = slideInVertically(
                animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                initialOffsetY = { -it },
            ) + fadeIn(animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS)),
            exit = slideOutVertically(
                animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                targetOffsetY = { -it },
            ) + fadeOut(animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS)),
        ) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = { Text(collectionName) },
                        navigationIcon = {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::toggleTimelineDisplayMode) {
                                val compact = timelineDisplayMode == TimelineDisplayMode.COMPACT
                                Icon(
                                    imageVector = if (compact) {
                                        Icons.Outlined.PhotoLibrary
                                    } else {
                                        Icons.Outlined.ViewAgenda
                                    },
                                    contentDescription = if (compact) {
                                        "切換為媒體優先瀏覽"
                                    } else {
                                        "切換為高密度掃讀"
                                    },
                                )
                            }
                        },
                    )
                    if (availableSourceIds.size > 1) {
                        SourceFilterRow(
                            sourceIds = availableSourceIds,
                            selectedSourceId = selectedSourceId,
                            sourceNames = sourceNames,
                            sourceIconUrls = sourceIconUrls,
                            onSourceSelect = onSourceSelect,
                        )
                    }
                }
            }
        }
    }

}

/** A saved-source emission never resets position; only a changed user selection does. */
internal fun shouldResetTimelinePosition(
    currentSelectedSourceId: String?,
    requestedSourceId: String?,
): Boolean = currentSelectedSourceId != requestedSourceId

@Composable
private fun SourceFilterRow(
    sourceIds: List<String>,
    selectedSourceId: String?,
    sourceNames: Map<String, String>,
    sourceIconUrls: Map<String, String?>,
    onSourceSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all-sources") {
            FilterChip(
                selected = selectedSourceId == null,
                onClick = { onSourceSelect(null) },
                label = { Text("全部") },
            )
        }
        items(sourceIds, key = { it }) { sourceId ->
            FilterChip(
                selected = selectedSourceId == sourceId,
                onClick = { onSourceSelect(sourceId) },
                label = {
                    Text(
                        text = sourceNames[sourceId] ?: sourceId,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = sourceIconUrls[sourceId]?.let { iconUrl ->
                    {
                        AsyncImage(
                            model = resourceModelOrNull(iconUrl),
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun rememberBarsVisibilityScrollConnection(
    onBarsVisibilityChange: (Boolean) -> Unit,
): NestedScrollConnection {
    val currentOnBarsVisibilityChange by rememberUpdatedState(onBarsVisibilityChange)
    val thresholdPx = with(LocalDensity.current) { BarVisibilityScrollThreshold.toPx() }

    return remember(thresholdPx) {
        val visibilityTracker = BarsVisibilityScrollTracker(thresholdPx)
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                visibilityTracker.onScroll(available.y)?.let { visible ->
                    currentOnBarsVisibilityChange(visible)
                }
                return Offset.Zero
            }
        }
    }
}

internal class BarsVisibilityScrollTracker(
    private val threshold: Float,
) {
    private var accumulatedDelta = 0f

    fun onScroll(delta: Float): Boolean? {
        if (delta == 0f) return null
        if ((delta > 0f && accumulatedDelta < 0f) ||
            (delta < 0f && accumulatedDelta > 0f)
        ) {
            accumulatedDelta = 0f
        }
        accumulatedDelta += delta

        return when {
            accumulatedDelta <= -threshold -> false.also { accumulatedDelta = 0f }
            accumulatedDelta >= threshold -> true.also { accumulatedDelta = 0f }
            else -> null
        }
    }
}

@Composable
private fun SourceFailureNotice(
    failures: List<SourceLoadFailure>,
    onRetry: () -> Unit,
) {
    val boards = failures.map { it.boardName }.distinct().joinToString("、")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "部分來源暫時無法更新：$boards",
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) {
                Text("重試")
            }
        }
    }
}

@Composable
private fun AppendLoadFooter(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "無法載入更多內容",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) {
                Text("重試")
            }
        }
    }
}
