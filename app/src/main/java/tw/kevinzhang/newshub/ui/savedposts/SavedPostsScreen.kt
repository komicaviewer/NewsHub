package tw.kevinzhang.newshub.ui.savedposts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.collection.data.SavedPostEntity
import tw.kevinzhang.newshub.ui.component.BodySmallText
import tw.kevinzhang.newshub.ui.component.ThreadSummaryCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPostsScreen(
    onNavigateUp: () -> Unit,
    onThreadClick: (SavedPostEntity) -> Unit,
    viewModel: SavedPostsViewModel = hiltViewModel(),
) {
    val savedPosts by viewModel.savedPosts.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除所有收藏") },
            text = { Text("確定要刪除所有收藏的貼文嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("確定刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏貼文") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = savedPosts.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete all",
                            tint = MaterialTheme.colorScheme.error,
                        )
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
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(128.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BodySmallText("沒有更多資料")
                        }
                    }
                }
            }
        }
    }
}
