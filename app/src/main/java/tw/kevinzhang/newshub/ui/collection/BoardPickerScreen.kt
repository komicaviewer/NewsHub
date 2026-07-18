package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BoardPickerScreen(
    selectedBoards: Set<SelectedBoard>,
    onBoardToggle: (SelectedBoard) -> Unit,
    onConfirm: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: BoardPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var showSelectedOnly by rememberSaveable { mutableStateOf(false) }
    val selectedCount = selectedBoards.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("選擇看板") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onConfirm) {
                        Text("完成（$selectedCount）")
                    }
                },
            )
        },
    ) { contentPadding ->
        when (val state = uiState) {
            BoardPickerUiState.Loading -> LoadingContent(contentPadding)

            BoardPickerUiState.NoExtensions -> EmptyContent(
                contentPadding = contentPadding,
                message = "尚未安裝任何擴充來源",
            )

            is BoardPickerUiState.AllSourcesFailed -> ErrorContent(
                contentPadding = contentPadding,
                failures = state.failures,
                onRetry = viewModel::retry,
            )

            is BoardPickerUiState.Content -> BoardListContent(
                contentPadding = contentPadding,
                sourcesWithBoards = state.sources,
                failures = state.failures,
                selectedBoards = selectedBoards,
                query = query,
                showSelectedOnly = showSelectedOnly,
                onQueryChange = { query = it },
                onSelectedOnlyChange = { showSelectedOnly = it },
                onBoardToggle = onBoardToggle,
                onRetry = viewModel::retry,
            )
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(
    contentPadding: PaddingValues,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    contentPadding: PaddingValues,
    failures: List<BoardLoadFailure>,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "無法載入任何來源的看板",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = failures.joinToString("、") { it.source.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) { Text("重試") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardListContent(
    contentPadding: PaddingValues,
    sourcesWithBoards: List<SourceWithBoards>,
    failures: List<BoardLoadFailure>,
    selectedBoards: Set<SelectedBoard>,
    query: String,
    showSelectedOnly: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectedOnlyChange: (Boolean) -> Unit,
    onBoardToggle: (SelectedBoard) -> Unit,
    onRetry: () -> Unit,
) {
    val selectedBoardsByKey = remember(selectedBoards) { selectedBoards.associateBy(SelectedBoard::key) }
    val selectedKeys = selectedBoardsByKey.keys
    val visibleSources = remember(sourcesWithBoards, query, selectedKeys, showSelectedOnly) {
        filterSourceWithBoards(
            sources = sourcesWithBoards,
            query = query,
            selectedKeys = selectedKeys,
            selectedOnly = showSelectedOnly,
        )
    }
    val selectedCounts = remember(sourcesWithBoards, selectedKeys) {
        selectedBoardCountsBySource(sourcesWithBoards, selectedKeys)
    }
    val totalCounts = remember(sourcesWithBoards) {
        sourcesWithBoards.associate { it.source.id to it.boards.size }
    }
    val visibleBoardCount = remember(visibleSources) { visibleSources.sumOf { it.boards.size } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        item(key = "search_and_filter") {
            BoardSearchAndFilter(
                query = query,
                showSelectedOnly = showSelectedOnly,
                selectedCount = selectedBoards.size,
                onQueryChange = onQueryChange,
                onSelectedOnlyChange = onSelectedOnlyChange,
            )
        }

        if (failures.isNotEmpty()) {
            item(key = "load_failures") {
                LoadFailureBanner(
                    failures = failures,
                    onRetry = onRetry,
                )
            }
        }

        if (visibleBoardCount == 0) {
            item(key = "empty_results") {
                val message = when {
                    query.isNotBlank() -> "找不到符合「${query.trim()}」的看板"
                    showSelectedOnly -> "尚未選擇任何看板"
                    else -> "目前沒有可用看板"
                }
                EmptyResultsContent(message)
            }
        } else {
            visibleSources.forEach { (source, boards) ->
                stickyHeader(key = "header:${source.id}") {
                    SourceHeader(
                        sourceName = source.name,
                        selectedCount = selectedCounts[source.id] ?: 0,
                        boardCount = totalCounts[source.id] ?: boards.size,
                    )
                }
                items(boards, key = { "${source.id}:${it.url}" }) { board ->
                    val selected = SelectedBoard(source.id, board.url, board.name)
                    val existingSelection = selectedBoardsByKey[selected.key]
                    BoardListItem(
                        boardName = board.name,
                        checked = existingSelection != null,
                        onToggle = { onBoardToggle(existingSelection ?: selected) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardSearchAndFilter(
    query: String,
    showSelectedOnly: Boolean,
    selectedCount: Int,
    onQueryChange: (String) -> Unit,
    onSelectedOnlyChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜尋看板名稱") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "清除搜尋")
                    }
                }
            } else {
                null
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showSelectedOnly,
                onClick = { onSelectedOnlyChange(false) },
                label = { Text("全部") },
            )
            FilterChip(
                selected = showSelectedOnly,
                onClick = { onSelectedOnlyChange(true) },
                label = { Text("已選 $selectedCount") },
            )
        }
    }
}

@Composable
private fun LoadFailureBanner(
    failures: List<BoardLoadFailure>,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "部分來源無法載入",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            failures.forEach { failure ->
                Text(
                    text = failure.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRetry) { Text("全部重試") }
        }
    }
}

@Composable
private fun EmptyResultsContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceHeader(
    sourceName: String,
    selectedCount: Int,
    boardCount: Int,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$selectedCount / $boardCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoardListItem(
    boardName: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics(mergeDescendants = true) {},
        headlineContent = {
            Text(
                text = boardName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
    )
}
