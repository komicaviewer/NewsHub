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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tw.kevinzhang.data.domain.CollectionEntity
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_loader.RepositoryTrustDomainState
import tw.kevinzhang.newshub.ui.component.TitleMediumText
import tw.kevinzhang.newshub.ui.component.appClickable
import tw.kevinzhang.newshub.ui.component.openExternalLink
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
    val repositoryDomainStates by viewModel.repositoryDomainStates.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openBoardWebsite: (Source, Board) -> Unit = { source, board ->
        coroutineScope.launch {
            try {
                val handle = source.getBoardWebUrl(board)
                val opened = handle != null && openExternalLink(
                    handle,
                    viewModel.resourceProvider::consumeExternalLink,
                    uriHandler::openUri,
                )
                if (!opened) snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            shouldShowNoExtensions(isLoading, sources) -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(boardsEmptyStateMessage(quarantinedExtensionCount, repositoryDomainStates.values))
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
                    sources.forEach { group ->
                        val source = group.source
                        val boards = group.boards
                        item(
                            key = "header:${source.id}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            SourceHeader(
                                source = source,
                                boardCount = sourceBoardCountForDisplay(group.loadState),
                                authState = authStates[source.id] ?: AuthState.Unknown,
                                onViewAll = { onNavigateToGroupDetail(source.id) },
                                onLoginClick = onLoginClick,
                                onLogoutClick = onLogoutClick,
                            )
                        }
                        when (val loadState = group.loadState) {
                            SourceBoardState.Loading -> item(
                                key = "loading:${source.id}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                SourceBoardLoading()
                            }
                            SourceBoardState.EmptySuccessfully -> item(
                                key = "empty:${source.id}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                SourceBoardEmpty()
                            }
                            is SourceBoardState.Failed -> item(
                                key = "failure:${source.id}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                SourceBoardFailureCard(
                                    source = source,
                                    failure = loadState.failure,
                                    onRetry = { viewModel.retrySource(source.id) },
                                    onLogin = { onLoginClick(source.id) },
                                )
                            }
                            is SourceBoardState.Ready -> Unit
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
                                    source = source,
                                    board = item.board,
                                    collections = collections,
                                    onOpenWebsite = openBoardWebsite,
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
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val openBoardWebsite: (Source, Board) -> Unit = { source, board ->
        coroutineScope.launch {
            try {
                val handle = source.getBoardWebUrl(board)
                val opened = handle != null && openExternalLink(
                    handle,
                    viewModel.resourceProvider::consumeExternalLink,
                    uriHandler::openUri,
                )
                if (!opened) snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(EXTERNAL_LINK_REJECTED_MESSAGE)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            group.loadState is SourceBoardState.Failed -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                SourceBoardFailureCard(
                    source = group.source,
                    failure = group.loadState.failure,
                    onRetry = { viewModel.retrySource(group.source.id) },
                    onLogin = { onLoginClick(group.source.id) },
                )
            }
            group.loadState == SourceBoardState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            group.loadState == SourceBoardState.EmptySuccessfully -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("此來源目前沒有看板") }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(group.boards, key = { "${group.source.id}:${it.url}" }) { board ->
                    BoardRow(
                        source = group.source,
                        board = board,
                        collections = collections,
                        onOpenWebsite = openBoardWebsite,
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

internal fun boardsEmptyStateMessage(
    quarantinedExtensionCount: Int,
    domainStates: Collection<RepositoryTrustDomainState> = emptyList(),
): String = when {
    RepositoryTrustDomainState.REVOKED in domainStates -> "Extension 來源信任已撤銷"
    RepositoryTrustDomainState.SUSPENDED in domainStates -> "Extension 來源已暫停"
    RepositoryTrustDomainState.EXPIRED in domainStates -> "Extension 來源中繼資料已過期"
    quarantinedExtensionCount > 0 -> "已安裝的 Extension 無法通過安全驗證"
    else -> "尚未安裝任何 Extension"
}

internal fun shouldShowNoExtensions(
    isLoading: Boolean,
    sources: List<SourceWithBoards>,
): Boolean = !isLoading && sources.isEmpty()

internal fun sourceBoardCountForDisplay(state: SourceBoardState): Int? = when (state) {
    SourceBoardState.EmptySuccessfully -> 0
    is SourceBoardState.Ready -> state.count.takeIf { it > 0 }
    is SourceBoardState.Failed, SourceBoardState.Loading -> null
}

@Composable
private fun SourceHeader(
    source: Source,
    boardCount: Int?,
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
            if (boardCount != null) {
                TextButton(onClick = onViewAll) {
                    Text("查看全部 $boardCount 個")
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (source is AuthenticatedSource) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SourceAuthAction(source, authState, onLoginClick, onLogoutClick)
            }
        }
    }
}

internal enum class SourceBoardFailureAction { RETRY, LOGIN }

internal fun sourceBoardFailureMessage(code: SourceFailureCode): String = when (code) {
    SourceFailureCode.HOST_POLICY -> "網站權限設定不完整"
    SourceFailureCode.ACCESS_CHALLENGE -> "網站要求額外的人機驗證"
    SourceFailureCode.ACCESS_DENIED -> "網站拒絕目前的存取方式"
    SourceFailureCode.AUTH_REQUIRED -> "需要登入才能載入看板"
    SourceFailureCode.AUTH_EXPIRED -> "登入已過期，請重新登入"
    SourceFailureCode.RATE_LIMITED -> "網站請求過於頻繁，請稍後重試"
    SourceFailureCode.SITE_UNAVAILABLE -> "目前無法連線網站"
    SourceFailureCode.PARSER_CONTRACT -> "網站內容格式已變更"
    SourceFailureCode.EXTENSION_RUNTIME -> "Extension 執行失敗"
    SourceFailureCode.TIMED_OUT -> "網站回應逾時"
    SourceFailureCode.TRUST_INACTIVE -> "Extension 來源目前未啟用"
    SourceFailureCode.INVALID_REQUEST -> "Extension 請求格式錯誤"
    SourceFailureCode.PAYLOAD_TOO_LARGE -> "Extension 回傳資料過大"
    SourceFailureCode.BACKPRESSURE -> "Extension 正忙碌，請稍後重試"
}

internal fun sourceBoardFailureAction(
    code: SourceFailureCode,
    supportsLogin: Boolean,
): SourceBoardFailureAction = if (
    supportsLogin && code in setOf(SourceFailureCode.AUTH_REQUIRED, SourceFailureCode.AUTH_EXPIRED)
) {
    SourceBoardFailureAction.LOGIN
} else {
    SourceBoardFailureAction.RETRY
}

@Composable
private fun SourceBoardLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text("正在重新載入看板…", modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SourceBoardEmpty() {
    Text(
        text = "此來源目前沒有看板",
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SourceBoardFailureCard(
    source: Source,
    failure: SourceFailure,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sourceBoardFailureMessage(failure.code), style = MaterialTheme.typography.titleSmall)
                if (failure.code == SourceFailureCode.HOST_POLICY && failure.observedHost != null) {
                    Text(
                        text = "未授權網域：${failure.observedHost}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            when (sourceBoardFailureAction(failure.code, source is AuthenticatedSource)) {
                SourceBoardFailureAction.LOGIN -> TextButton(onClick = onLogin) { Text("前往登入") }
                SourceBoardFailureAction.RETRY -> TextButton(onClick = onRetry) { Text("重試") }
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
    source: Source,
    board: Board,
    collections: List<CollectionEntity>,
    onOpenWebsite: (Source, Board) -> Unit,
    onAddToCollections: (List<String>) -> Unit,
) {
    BoardItem(source, board, collections, onOpenWebsite, onAddToCollections, compact = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardRow(
    source: Source,
    board: Board,
    collections: List<CollectionEntity>,
    onOpenWebsite: (Source, Board) -> Unit,
    onAddToCollections: (List<String>) -> Unit,
) {
    BoardItem(
        source = source,
        board = board,
        collections = collections,
        onOpenWebsite = onOpenWebsite,
        onAddToCollections = onAddToCollections,
        compact = false,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardItem(
    source: Source,
    board: Board,
    collections: List<CollectionEntity>,
    onOpenWebsite: (Source, Board) -> Unit,
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
                        onClick = { onOpenWebsite(source, board) },
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
                    TextButton(onClick = { onOpenWebsite(source, board) }) {
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

private const val EXTERNAL_LINK_REJECTED_MESSAGE = "網站連結被安全政策阻擋或已失效"
