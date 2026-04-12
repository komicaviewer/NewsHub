package tw.kevinzhang.newshub.ui.boards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import coil.compose.AsyncImage
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.newshub.auth.LoginStatus
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.component.TitleMediumText
import tw.kevinzhang.newshub.ui.component.appClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardsScreen(
    onNavigateToMarketplace: () -> Unit,
    onLoginClick: (sourceId: String) -> Unit = {},
    onLogoutClick: (sourceId: String) -> Unit = {},
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginStatuses by viewModel.loginStatuses.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boards") },
                actions = {
                    TextButton(onClick = onNavigateToMarketplace) {
                        Text("Marketplace")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                isLoading -> Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                sources.isEmpty() -> Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No extensions installed. Browse the Marketplace to install some.")
                }
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    sources.forEach { (source, boards) ->
                        stickyHeader(key = source.id) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (source.iconUrl != null) {
                                            AsyncImage(
                                                model = source.iconUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        TitleMediumText(
                                            text = source.name,
                                        )
                                    }
                                    if (source.needsLogin) {
                                        val status = loginStatuses[source.id] ?: LoginStatus.NONE
                                        when (status) {
                                            LoginStatus.LOGGED_IN -> TextButton(
                                                onClick = { onLogoutClick(source.id) },
                                            ) { Text("Logout") }
                                            LoginStatus.NONE -> TextButton(
                                                onClick = { onLoginClick(source.id) },
                                            ) { Text("Login") }
                                        }
                                    }
                                }
                            }
                        }
                        items(boards, key = { "${source.id}:${it.url}" }) { board ->
                            BoardRow(
                                board = board,
                                collections = collections,
                                onAddToCollections = { collectionIds ->
                                    viewModel.addBoardToCollections(collectionIds, board, source)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRow(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollections: (List<String>) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    AppCard(onClick = { showSheet = true }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = board.name, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, board.url.toUri())
                context.startActivity(intent)
            }) {
                Icon(Icons.Outlined.Language, contentDescription = "Open in browser")
            }
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Add to collection")
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedIds = emptySet()
                showSheet = false
            },
            sheetState = sheetState,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TitleMediumText(
                    text = "加入 Collection",
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onAddToCollections(selectedIds.toList())
                        selectedIds = emptySet()
                        showSheet = false
                    },
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Text("確認")
                }
            }

            if (collections.isEmpty()) {
                Text(
                    text = "尚未建立任何 Collection",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                ) {
                    items(collections, key = { it.id }) { collection ->
                        val isChecked = collection.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appClickable {
                                    selectedIds = if (collection.id in selectedIds)
                                        selectedIds - collection.id
                                    else
                                        selectedIds + collection.id
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                            Text(
                                text = "${collection.emoji}  ${collection.name}",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
