package tw.kevinzhang.newshub.ui.boards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.newshub.ui.component.TitleMediumText
import tw.kevinzhang.newshub.ui.component.appClickable
import tw.kevinzhang.newshub.ui.component.resourceModelOrNull

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
    val quarantinedExtensionCount by viewModel.quarantinedExtensionCount.collectAsStateWithLifecycle()

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
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(boardsEmptyStateMessage(quarantinedExtensionCount))
                    TextButton(onClick = onNavigateToMarketplace) {
                        Text("前往 Marketplace")
                    }
                }
            }
            else -> BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                val columns = if (maxWidth >= 600.dp) 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sources.forEach { (source, boards) ->
                        item(
                            key = "header:${source.id}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            SourceHeader(
                                source = source,
                                boardCount = boards.size,
                                authState = authStates[source.id] ?: AuthState.Unknown,
                                onViewAll = { onNavigateToGroupDetail(source.id) },
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
                                    remainingCount = (boards.size - 5).coerceAtLeast(0),
                                    onClick = { onNavigateToGroupDetail(source.id) },
                                )
                            }
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

internal fun boardsEmptyStateMessage(quarantinedExtensionCount: Int): String =
    if (quarantinedExtensionCount > 0) {
        "已安裝的 Extension 無法通過安全驗證"
    } else {
        "尚未安裝任何 Extension"
    }

@Composable
private fun SourceHeader(
    source: Source,
    boardCount: Int,
    authState: AuthState,
    onViewAll: () -> Unit,
    onLoginClick: (String) -> Unit,
    onLogoutClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (source.iconUrl != null) {
                    AsyncImage(
                        model = resourceModelOrNull(source.iconUrl),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                TitleMediumText(text = source.name)
            }
            TextButton(onClick = onViewAll) {
                Text("查看全部 $boardCount 個")
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (source is AuthenticatedSource) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SourceAuthAction(source, authState, onLoginClick, onLogoutClick)
            }
        }
    }
}

@Composable
private fun SourceAuthAction(
    source: Source,
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
private fun MoreBoardsCard(remainingCount: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(132.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text("查看更多", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "還有 $remainingCount 個看板",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.align(Alignment.End),
            )
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
    BoardItem(
        board = board,
        collections = collections,
        onAddToCollections = onAddToCollections,
        compact = false,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardItem(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollections: (List<String>) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    Surface(
        modifier = modifier.then(if (compact) Modifier.height(132.dp) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        // A raw URL from an extension is data, not executable navigation authority.
        onClick = {},
    ) {
        if (compact) {
            Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 6.dp)) {
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {},
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("網站", modifier = Modifier.padding(start = 4.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { showSheet = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add to collection")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = board.name, style = MaterialTheme.typography.titleMedium)
                board.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = {}, enabled = false) {
                        Icon(
                            Icons.Outlined.Language,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("開啟網站", modifier = Modifier.padding(start = 6.dp))
                    }
                    FilledTonalIconButton(
                        onClick = { showSheet = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add to collection")
                    }
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
