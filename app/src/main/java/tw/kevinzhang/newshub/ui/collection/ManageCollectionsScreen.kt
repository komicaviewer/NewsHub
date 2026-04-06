package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.TitleMediumText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageCollectionsScreen(
    onNavigateUp: () -> Unit,
    onEditCollection: (collectionId: String) -> Unit,
    viewModel: ManageCollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    var localOrder by remember { mutableStateOf(emptyList<CollectionEntity>()) }
    var pendingReorder by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localOrder = localOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        pendingReorder = true
    }

    // Sync from DB only when not dragging
    LaunchedEffect(collections) {
        if (!reorderState.isAnyItemDragging) {
            localOrder = collections
        }
    }

    // Persist reorder only when drag ends
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging && pendingReorder) {
            viewModel.reorderCollections(localOrder.map { it.id })
            pendingReorder = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理 Collections") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (localOrder.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                BodyLargeText(
                    text = "尚無 Collections",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(localOrder, key = { it.id }) { collection ->
                    ReorderableItem(reorderState, key = collection.id) { isDragging ->
                        CollectionManageRow(
                            collection = collection,
                            isDragging = isDragging,
                            dragModifier = Modifier.draggableHandle(),
                            onEditClick = { onEditCollection(collection.id) },
                            onDeleteClick = { pendingDeleteId = collection.id },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val name = localOrder.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("刪除 Collection") },
            text = { Text("確定要刪除「$name」嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(id)
                    pendingDeleteId = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CollectionManageRow(
    collection: CollectionEntity,
    isDragging: Boolean,
    dragModifier: Modifier,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDragging) 8.dp else 0.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "拖曳排序",
            modifier = dragModifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TitleMediumText(
            text = collection.emoji,
            modifier = Modifier.padding(end = 12.dp),
        )
        BodyLargeText(
            text = collection.name,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEditClick) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "編輯",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "刪除",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
