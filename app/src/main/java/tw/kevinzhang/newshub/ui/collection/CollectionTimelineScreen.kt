package tw.kevinzhang.newshub.ui.collection

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.news.CardHeadRepliesBlock
import tw.kevinzhang.newshub.ui.news.CardHeadTextBlock
import tw.kevinzhang.newshub.ui.news.CardHeadTimeBlock

@Composable
fun CollectionTimelineScreen(
    onThreadClick: (ThreadSummary) -> Unit,
    viewModel: CollectionTimelineViewModel = hiltViewModel(),
) {
    val items = viewModel.timelinePager.collectAsLazyPagingItems()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn {
            items(count = items.itemCount) { index ->
                val summary = items[index]
                if (summary != null) {
                    ThreadSummaryCard(summary = summary, onClick = { onThreadClick(summary) })
                }
            }
            item {
                when (items.loadState.append) {
                    is LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensionResource(R.dimen.space_8)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    is LoadState.Error -> Text("Failed to load more")
                    else -> {}
                }
            }
        }
        when (items.loadState.refresh) {
            is LoadState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is LoadState.Error -> Text("Error loading timeline", modifier = Modifier.align(Alignment.Center))
            else -> {}
        }
    }
}

@Composable
private fun ThreadSummaryCard(summary: ThreadSummary, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.space_8))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row {
                    CardHeadTimeBlock(summary.createdAt)
                    CardHeadTextBlock(summary.author)
                }
                Row {
                    CardHeadTextBlock(summary.sourceId)
                    CardHeadRepliesBlock(summary.replyCount, showZero = false)
                }
            }
            summary.title?.let { title ->
                if (title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
            }
            summary.thumbnail?.let { url ->
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
