package tw.kevinzhang.newshub.ui.collection

import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTimelineScreen(
    onOpenDrawer: () -> Unit,
    onThreadClick: (ThreadSummary) -> Unit,
    viewModel: CollectionTimelineViewModel = hiltViewModel(),
) {
    val items = viewModel.timelinePager.collectAsLazyPagingItems()
    val collectionName by viewModel.collectionName.collectAsStateWithLifecycle()
    val rawImageSourceIds by viewModel.rawImageSourceIds.collectAsStateWithLifecycle()

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
        ) {
            LazyColumn {
                items(
                    count = items.itemCount,
                    key = { index -> items.peek(index)?.id ?: index }) { index ->
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
                                .padding(dimensionResource(R.dimen.space_8)),
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
        }
    }
}

@Composable
private fun ThreadSummaryCard(summary: ThreadSummary, alwaysUseRawImage: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.space_8))) {
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
                            modifier = Modifier.padding(end = dimensionResource(R.dimen.space_4)),
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
                        modifier = Modifier.padding(end = dimensionResource(R.dimen.space_4)),
                    )
                    summary.replyCount?.takeIf { it > 0 }?.let {
                        Text(text = "$it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            summary.title?.let { title ->
                if (title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
            }

            val imageUrl = if (alwaysUseRawImage) {
                summary.previewContent
                    .filterIsInstance<Paragraph.ImageInfo>()
                    .firstOrNull()?.raw ?: summary.thumbnail
            } else {
                summary.thumbnail
            }
            imageUrl?.let { url ->
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_8)))
}
