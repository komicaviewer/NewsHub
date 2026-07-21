package tw.kevinzhang.newshub.ui.collection

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.data.TimelineDisplayMode
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    onOpenDrawer: () -> Unit,
    onThreadClick: (ThreadSummary, boardName: String?) -> Unit,
    onNavigateToBoardPicker: () -> Unit,
    onNavigateToBoards: () -> Unit,
    scrollToTopTrigger: Int = 0,
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
    val authenticationRequiredNotice by viewModel.authenticationRequiredNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val listState = rememberLazyListState()
    val activity = LocalContext.current as Activity

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
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
        topBar = {
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

            // Empty state: subscriptions loaded (non-null) and empty, pager not loading
            if (subscriptions?.isEmpty() == true && items.loadState.refresh !is LoadState.Loading) {
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
            }

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
