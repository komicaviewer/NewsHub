package tw.kevinzhang.newshub.ui.boards

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.component.TitleMediumText
import tw.kevinzhang.newshub.ui.component.appClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardsScreen(
    onNavigateToMarketplace: () -> Unit,
    onNavigateToGroupDetail: (sourceId: String) -> Unit,
    onLoginClick: (sourceId: String) -> Unit = {},
    onLogoutClick: (sourceId: String) -> Unit = {},
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val authStates by viewModel.authStates.collectAsStateWithLifecycle()

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
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            sources.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("No extensions installed. Browse the Marketplace to install some.") }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sources.forEach { (source, boards) ->
                    item(
                        key = "header:${source.id}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        SourceHeader(
                            source = source,
                            authState = authStates[source.id] ?: AuthState.Unknown,
                            onLoginClick = onLoginClick,
                            onLogoutClick = onLogoutClick,
                        )
                    }
                    items(
                        items = buildBoardGroupItems(boards),
                        key = { item ->
                            when (item) {
                                is BoardGroupItem.BoardCard -> "${source.id}:${item.board.url}"
                                BoardGroupItem.More -> "more:${source.id}"
                            }
                        },
                    ) { item ->
                        when (item) {
                            is BoardGroupItem.BoardCard -> BoardGridCard(
                                board = item.board,
                                collections = collections,
                                onAddToCollections = { collectionIds ->
                                    viewModel.addBoardToCollections(collectionIds, item.board, source)
                                },
                            )
                            BoardGroupItem.More -> MoreBoardsCard(
                                onClick = { onNavigateToGroupDetail(source.id) },
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
fun BoardGroupDetailScreen(
    sourceId: String,
    onNavigateUp: () -> Unit,
    onLoginClick: (sourceId: String) -> Unit = {},
    onLogoutClick: (sourceId: String) -> Unit = {},
    viewModel: BoardsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val authStates by viewModel.authStates.collectAsStateWithLifecycle()
    val group = sources.firstOrNull { it.source.id == sourceId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.source?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    group?.source?.let { source ->
                        SourceAuthAction(
                            source = source,
                            authState = authStates[source.id] ?: AuthState.Unknown,
                            onLoginClick = onLoginClick,
                            onLogoutClick = onLogoutClick,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            group == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("找不到此 group") }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(group.boards, key = { "${group.source.id}:${it.url}" }) { board ->
                    BoardRow(
                        board = board,
                        collections = collections,
                        onAddToCollections = { collectionIds ->
                            viewModel.addBoardToCollections(collectionIds, board, group.source)
                        },
                    )
                }
            }
        }
    }
}

internal sealed interface BoardGroupItem {
    data class BoardCard(val board: Board) : BoardGroupItem
    data object More : BoardGroupItem
}

internal fun buildBoardGroupItems(boards: List<Board>): List<BoardGroupItem> =
    boards.take(5).map(BoardGroupItem::BoardCard) +
        if (boards.size > 5) listOf(BoardGroupItem.More) else emptyList()

@Composable
private fun SourceHeader(
    source: tw.kevinzhang.extension_api.Source,
    authState: AuthState,
    onLoginClick: (String) -> Unit,
    onLogoutClick: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
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
                TitleMediumText(text = source.name)
            }
            SourceAuthAction(source, authState, onLoginClick, onLogoutClick)
        }
    }
}

@Composable
private fun SourceAuthAction(
    source: tw.kevinzhang.extension_api.Source,
    authState: AuthState,
    onLoginClick: (String) -> Unit,
    onLogoutClick: (String) -> Unit,
) {
    if (source !is AuthenticatedSource) return
    when (authState) {
        AuthState.SignedIn -> TextButton(onClick = { onLogoutClick(source.id) }) { Text("Logout") }
        AuthState.SigningIn -> Text("登入中…")
        AuthState.Expired -> TextButton(onClick = { onLoginClick(source.id) }) { Text("重新登入") }
        AuthState.SignedOut, AuthState.Unknown ->
            TextButton(onClick = { onLoginClick(source.id) }) { Text("Login") }
    }
}

@Composable
private fun MoreBoardsCard(onClick: () -> Unit) {
    AppCard(modifier = Modifier.height(116.dp), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("查看更多", style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun BoardGridCard(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollections: (List<String>) -> Unit,
) {
    BoardItem(board, collections, onAddToCollections, compact = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRow(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollections: (List<String>) -> Unit,
) {
    BoardItem(board, collections, onAddToCollections, compact = false)
    Spacer(modifier = Modifier.height(4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardItem(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollections: (List<String>) -> Unit,
    compact: Boolean,
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    AppCard(
        modifier = if (compact) Modifier.height(116.dp) else Modifier,
        onClick = { showSheet = true },
    ) {
        val openWebsite = {
            val intent = Intent(Intent.ACTION_VIEW, board.url.toUri())
            context.startActivity(intent)
        }
        if (compact) {
            Column(modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 4.dp)) {
                Text(
                    text = board.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(modifier = Modifier.align(Alignment.End)) {
                    IconButton(onClick = openWebsite) {
                        Icon(Icons.Outlined.Language, contentDescription = "Open in browser")
                    }
                    IconButton(onClick = { showSheet = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add to collection")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = board.name, modifier = Modifier.weight(1f))
                IconButton(onClick = openWebsite) {
                    Icon(Icons.Outlined.Language, contentDescription = "Open in browser")
                }
                IconButton(onClick = { showSheet = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add to collection")
                }
            }
        }
    }

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
