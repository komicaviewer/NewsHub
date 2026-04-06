package tw.kevinzhang.newshub.ui.collection

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    onOpenDrawer: () -> Unit,
    onThreadClick: (ThreadSummary) -> Unit,
    scrollToTopTrigger: Int = 0,
    viewModel: CollectionTimelineViewModel = hiltViewModel(),
    boardPickerViewModel: BoardPickerViewModel = hiltViewModel(),
) {
    val items = viewModel.timelinePager.collectAsLazyPagingItems()
    val collectionName by viewModel.collectionName.collectAsStateWithLifecycle()
    val rawImageSourceIds by viewModel.rawImageSourceIds.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val sourcesWithBoards by boardPickerViewModel.sourcesWithBoards.collectAsStateWithLifecycle()
    val isBoardPickerLoading by boardPickerViewModel.isLoading.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val activity = LocalContext.current as Activity
    val pullToRefreshState = rememberPullToRefreshState()

    var showBoardPicker by remember { mutableStateOf(false) }
    var localSelectedBoards by remember { mutableStateOf(emptySet<SelectedBoard>()) }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) { items.refresh() }
    }
    LaunchedEffect(items.loadState.refresh) {
        if (items.loadState.refresh !is LoadState.Loading) {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
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
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            // Empty state: subscriptions loaded (non-null) and empty, pager not loading
            if (subscriptions?.isEmpty() == true && items.loadState.refresh !is LoadState.Loading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "尚未加入任何 Board",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showBoardPicker = true }) {
                        Text("新增 Board")
                    }
                }
            }

            LazyColumn(state = listState) {
                items(
                    count = items.itemCount,
                    key = { index -> items.peek(index)?.id ?: index },
                ) { index ->
                    val summary = items[index] ?: return@items
                    ThreadSummaryCard(
                        summary = summary,
                        alwaysUseRawImage = summary.sourceId in rawImageSourceIds,
                        onClick = { onThreadClick(summary) },
                    )
                }
                item {
                    when (val appendState = items.loadState.append) {
                        is LoadState.Loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        is LoadState.Error -> {
                            LaunchedEffect(appendState.error) {
                                Log.e("CollectionTimeline", "Append load failed", appendState.error)
                            }
                            Text("Failed to load more")
                        }

                        else -> {}
                    }
                }
            }

            when (val refreshState = items.loadState.refresh) {
                is LoadState.Loading -> if (items.itemCount == 0) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is LoadState.Error -> {
                    LaunchedEffect(refreshState.error) {
                        Log.e("CollectionTimeline", "Refresh failed", refreshState.error)
                    }
                    if (items.itemCount == 0) {
                        Text(
                            text = "Error loading timeline",
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                else -> {}
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    if (showBoardPicker) {
        BoardPickerDialog(
            sourcesWithBoards = sourcesWithBoards,
            isLoading = isBoardPickerLoading,
            selectedBoards = localSelectedBoards,
            onBoardToggle = { board ->
                localSelectedBoards = if (board in localSelectedBoards)
                    localSelectedBoards - board
                else
                    localSelectedBoards + board
            },
            onConfirm = {
                localSelectedBoards.forEach { board ->
                    viewModel.addBoardSubscription(board.sourceId, board.boardUrl, board.boardName)
                }
                localSelectedBoards = emptySet()
                showBoardPicker = false
            },
            onDismiss = {
                localSelectedBoards = emptySet()
                showBoardPicker = false
            },
        )
    }
}

@Composable
private fun ThreadSummaryCard(
    summary: ThreadSummary,
    alwaysUseRawImage: Boolean,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    summary.createdAt?.let {
                        Text(
                            text = android.text.format.DateUtils.getRelativeTimeSpanString(it)
                                .toString(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    summary.author?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    Text(
                        text = summary.sourceId,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    summary.replyCount?.takeIf { it > 0 }?.let {
                        Text(text = "$it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            summary.title?.let { title ->
                if (title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
            }

            summary.previewContent.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> Text(paragraph.content)
                    is Paragraph.Quote -> Text(
                        "> ${paragraph.content}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is Paragraph.Link -> Text(
                        paragraph.content,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    else -> {}
                }
            }

            val url = if (alwaysUseRawImage) summary.rawImage else summary.thumbnail
            url?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
