package tw.kevinzhang.newshub.ui.collection

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    onOpenDrawer: () -> Unit,
    onThreadClick: (ThreadSummary) -> Unit,
    onNavigateToBoardPicker: () -> Unit,
    scrollToTopTrigger: Int = 0,
    viewModel: CollectionTimelineViewModel = hiltViewModel(),
) {
    val items = viewModel.timelinePager.collectAsLazyPagingItems()
    val collectionName by viewModel.collectionName.collectAsStateWithLifecycle()
    val rawImageSourceIds by viewModel.rawImageSourceIds.collectAsStateWithLifecycle()
    val sourceIconUrls: Map<String, String?> by viewModel.sourceIconUrls.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val activity = LocalContext.current as Activity
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }

    BackHandler { activity.moveTaskToBack(true) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(collectionName) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = items.loadState.refresh is LoadState.Loading,
            onRefresh = { items.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(
                    count = items.itemCount,
                ) { index ->
                    val summary = items[index] ?: return@items
                    ThreadSummaryCard(
                        summary = summary,
                        alwaysUseRawImage = summary.sourceId in rawImageSourceIds,
                        sourceIconUrl = sourceIconUrls[summary.sourceId],
                        onClick = { onThreadClick(summary) },
                    )
                }
                item {
                    when (val appendState = items.loadState.append) {
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

