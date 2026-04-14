package tw.kevinzhang.newshub.ui.savedposts

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
import tw.kevinzhang.collection.data.SavedPostEntity
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPostsScreen(
    onNavigateUp: () -> Unit,
    onThreadClick: (SavedPostEntity) -> Unit,
    viewModel: SavedPostsViewModel = hiltViewModel(),
) {
    val savedPosts by viewModel.savedPosts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏貼文") },
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
            if (savedPosts.isEmpty()) {
                Text(
                    text = "尚無收藏貼文",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(savedPosts, key = { "${it.sourceId}:${it.threadId}" }) { entity ->
                        val summary = entity.toThreadSummary()
                        ThreadSummaryCard(
                            summary = summary,
                            alwaysUseRawImage = false,
                            sourceIconUrl = entity.sourceIconUrl,
                            onClick = { onThreadClick(entity) },
                        )
                    }
                }
            }
        }
    }
}
