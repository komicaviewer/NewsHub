package tw.kevinzhang.newshub.ui.collection

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

private const val BAR_VISIBILITY_ANIMATION_MILLIS = 220
private val BarVisibilityScrollThreshold = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    onOpenDrawer: () -> Unit,
    onThreadClick: (ThreadSummary, boardName: String?) -> Unit,
    onNavigateToBoardPicker: () -> Unit,
    onNavigateToBoards: () -> Unit,
    scrollToTopTrigger: Int = 0,
    barsVisible: Boolean = true,
    onBarsVisibilityChange: (Boolean) -> Unit = {},
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

    val listState = rememberLazyListState()
    val activity = LocalContext.current as Activity
    val barsScrollConnection = rememberBarsVisibilityScrollConnection(
        onBarsVisibilityChange = onBarsVisibilityChange,
    )

    LaunchedEffect(Unit) {
        onBarsVisibilityChange(true)
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            onBarsVisibilityChange(true)
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(selectedSourceId) {
        onBarsVisibilityChange(true)
        listState.scrollToItem(0)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { isAtTop ->
                if (isAtTop) onBarsVisibilityChange(true)
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

    Scaffold(
        modifier = Modifier.nestedScroll(barsScrollConnection),
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars),
                )
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically(
                        animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                        initialOffsetY = { -it },
                    ) + expandVertically(
                        animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                        expandFrom = Alignment.Top,
                    ) + fadeIn(animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS)),
                    exit = slideOutVertically(
                        animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                        targetOffsetY = { -it },
                    ) + shrinkVertically(
                        animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS),
                        shrinkTowards = Alignment.Top,
                    ) + fadeOut(animationSpec = tween(BAR_VISIBILITY_ANIMATION_MILLIS)),
                ) {
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
                                        imageVector = if (compact) Icons.Outlined.PhotoLibrary else Icons.Outlined.ViewAgenda,
                                        contentDescription = if (compact) "切換為媒體優先瀏覽" else "切換為高密度掃讀",
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
                                onSourceSelect = viewModel::selectSource,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = items.loadState.refresh is LoadState.Loading,
            onRefresh = {
                viewModel.clearSourceLoadFailures()
                items.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                        val subscription = subscriptions?.firstOrNull {
                            it.sourceId == summary.sourceId && it.boardUrl == summary.boardUrl
                        }
                        ThreadSummaryCard(
                            summary = summary,
                            alwaysUseRawImage = summary.sourceId in rawImageSourceIds,
                            sourceIconUrl = sourceIconUrls[summary.sourceId],
                            sourceName = sourceNames[summary.sourceId],
                            boardName = subscription?.boardName,
                            displayMode = timelineDisplayMode,
                            isRead = (summary.sourceId to summary.id) in readThreadKeys,
                            onClick = { onThreadClick(summary, subscription?.boardName) },
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
                    Button(onClick = { viewModel.selectSource(null) }) {
                        Text("顯示全部")
                    }
                }
            }

        }
    }

}

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
                            model = iconUrl,
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
