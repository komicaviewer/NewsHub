package tw.kevinzhang.newshub.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.collection.data.ReadingHistoryEntity
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingHistoryScreen(
    onNavigateUp: () -> Unit,
    onThreadClick: (ThreadSummary) -> Unit,
    viewModel: ReadingHistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("閱讀紀錄") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (history.isEmpty()) {
                Text(
                    text = "尚無閱讀紀錄",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { "${it.sourceId}:${it.threadId}" }) { entity ->
                        val summary = entity.toThreadSummary()
                        ThreadSummaryCard(
                            summary = summary,
                            alwaysUseRawImage = false,
                            sourceIconUrl = entity.sourceIconUrl,
                            onClick = { onThreadClick(summary) },
                        )
                    }
                }
            }
        }
    }
}
