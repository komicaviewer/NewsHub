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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.data.domain.CanonicalSourceIdentities
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.BoardCategory

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
                        Text("完成（${selectedBoards.size}）")
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

            is BoardPickerUiState.Content -> BoardCatalogContent(
                contentPadding = contentPadding,
                state = state,
                selectedBoards = selectedBoards,
                onQueryChange = viewModel::updateQuery,
                onSourceSelect = viewModel::selectSource,
                onCategorySelect = viewModel::selectCategory,
                onBoardToggle = onBoardToggle,
                onLoadNextPage = viewModel::loadNextPage,
                onRetry = viewModel::retry,
                onRetrySource = viewModel::retrySource,
            )
        }
    }
}

@Composable
private fun LoadingContent(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun EmptyContent(contentPadding: PaddingValues, message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorContent(
    contentPadding: PaddingValues,
    failures: List<BoardLoadFailure>,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("無法載入任何來源的看板", style = MaterialTheme.typography.titleMedium)
            Text(
                failures.joinToString("、") { it.source.name },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onRetry) { Text("重試") }
        }
    }
}

/**
 * The catalog is deliberately source-grouped: equal board names remain unambiguous, while a
 * search still starts across every installed source. The ViewModel owns remote requests,
 * debouncing, pagination, and the recent-result cache; this composable only renders that state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardCatalogContent(
    contentPadding: PaddingValues,
    state: BoardPickerUiState.Content,
    selectedBoards: Set<SelectedBoard>,
    onQueryChange: (String) -> Unit,
    onSourceSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onBoardToggle: (SelectedBoard) -> Unit,
    onLoadNextPage: (String) -> Unit,
    onRetry: () -> Unit,
    onRetrySource: (String) -> Unit,
) {
    val selectedByKey = remember(selectedBoards) { selectedBoards.associateBy(SelectedBoard::key) }
    val visibleSources = remember(state.sources, state.selectedSourceId, state.query, selectedByKey) {
        state.sources
            .filter { state.selectedSourceId == null || it.source.id == state.selectedSourceId }
            .map { group ->
                if (state.query.isBlank()) {
                    group.copy(
                        boards = group.boards.filterNot { board ->
                            selectedBoardKey(group.source.id, board.url) in selectedByKey
                        },
                    )
                } else {
                    group
                }
            }
            .filter {
                it.boards.isNotEmpty() || it.nextPageToken != null || it.isAppending || it.appendFailure != null
            }
    }
    val selectedSource = remember(state.sources, state.selectedSourceId) {
        state.sources.firstOrNull { it.source.id == state.selectedSourceId }
    }
    val sourceNames = remember(state.allSources) { state.allSources.associate { it.id to it.name } }
    val landingSelectedBoards = remember(selectedBoards, state.selectedSourceId) {
        selectedBoards.filter { state.selectedSourceId == null || it.sourceId == state.selectedSourceId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        item(key = "catalog_controls") {
            CatalogControls(
                query = state.query,
                sources = state.allSources,
                selectedSourceId = state.selectedSourceId,
                categories = selectedSource?.categories.orEmpty(),
                selectedCategoryId = state.selectedCategoryId,
                onQueryChange = onQueryChange,
                onSourceSelect = onSourceSelect,
                onCategorySelect = onCategorySelect,
            )
        }

        if (state.isRefreshing) {
            item(key = "refreshing") { RefreshingResultsContent() }
        }

        if (state.failures.isNotEmpty()) {
            item(key = "initial_load_failures") {
                LoadFailureBanner(failures = state.failures, onRetry = onRetry)
            }
        }

        // The landing page intentionally puts subscriptions first, then the popular catalog.
        if (state.query.isBlank() && landingSelectedBoards.isNotEmpty()) {
            item(key = "selected_header") { SelectedBoardsHeader(landingSelectedBoards.size) }
            items(landingSelectedBoards.sortedWith(compareBy(SelectedBoard::sourceId, SelectedBoard::boardName)), key = { "selected:${it.key}" }) { selected ->
                BoardListItem(
                    boardName = selected.boardName,
                    sourceName = sourceNames[selected.sourceId],
                    checked = true,
                    onToggle = { onBoardToggle(selected) },
                )
            }
        }

        if (!state.isRefreshing && visibleSources.all { it.boards.isEmpty() }) {
            item(key = "empty_results") {
                EmptyResultsContent(
                    when {
                        state.query.isNotBlank() -> "找不到符合「${state.query.trim()}」的看板"
                        else -> "目前沒有可用看板"
                    },
                )
            }
        }

        visibleSources.forEach { sourceWithBoards ->
            val source = sourceWithBoards.source
            stickyHeader(key = "header:${source.id}") {
                SourceHeader(
                    sourceName = source.name,
                    selectedCount = selectedBoards.count { it.sourceId == source.id },
                    isFromCache = sourceWithBoards.isFromCache,
                )
            }
            items(sourceWithBoards.boards, key = { "${source.id}:${it.url}" }) { board ->
                val identity = source.sourceIdentity ?: return@items
                val selection = SelectedBoard(
                    sourceId = source.id,
                    sourceKey = CanonicalSourceIdentities.fromRuntimeIdentity(identity).sourceKey,
                    boardUrl = board.url,
                    boardName = board.name,
                    sourceIdentity = identity,
                )
                val existing = selectedByKey[selection.key]
                BoardListItem(
                    boardName = board.name,
                    checked = existing != null,
                    onToggle = { onBoardToggle(existing ?: selection) },
                )
            }
            item(key = "paging:${source.id}") {
                SourcePagingFooter(
                    source = source,
                    nextPageToken = sourceWithBoards.nextPageToken,
                    isAppending = sourceWithBoards.isAppending,
                    appendFailure = sourceWithBoards.appendFailure,
                    onLoadNextPage = { onLoadNextPage(source.id) },
                    onRetry = { onRetrySource(source.id) },
                )
            }
        }
    }
}

@Composable
private fun CatalogControls(
    query: String,
    sources: List<Source>,
    selectedSourceId: String?,
    categories: List<BoardCategory>,
    selectedCategoryId: String?,
    onQueryChange: (String) -> Unit,
    onSourceSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("搜尋所有來源的看板") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, "清除搜尋") } }
            } else null,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedSourceId == null,
                    onClick = { onSourceSelect(null) },
                    label = { Text("全部來源") },
                )
            }
            items(sources, key = Source::id) { source ->
                FilterChip(
                    selected = selectedSourceId == source.id,
                    onClick = { onSourceSelect(source.id) },
                    label = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        // Categories are source-owned. Showing them only after a source is selected avoids
        // implying that names such as "遊戲" are comparable between extensions.
        if (query.isBlank() && selectedSourceId != null && categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("全部") },
                    )
                }
                items(categories, key = BoardCategory::id) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { onCategorySelect(category.id) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadFailureBanner(failures: List<BoardLoadFailure>, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("部分來源無法載入", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            failures.forEach { failure ->
                Text(failure.source.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRetry) { Text("全部重試") }
        }
    }
}

@Composable
private fun SelectedBoardsHeader(count: Int) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = "已選看板（$count）",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyResultsContent(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) { Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun RefreshingResultsContent() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "正在更新看板…",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceHeader(sourceName: String, selectedCount: Int, isFromCache: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(sourceName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isFromCache) Text("最近結果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selectedCount > 0) Text("已選 $selectedCount", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourcePagingFooter(
    source: Source,
    nextPageToken: String?,
    isAppending: Boolean,
    appendFailure: Throwable?,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        isAppending -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        appendFailure != null -> Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${source.name} 的更多看板載入失敗", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重試") }
        }
        nextPageToken != null -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onLoadNextPage) { Text("載入更多") }
        }
    }
}

@Composable
private fun BoardListItem(
    boardName: String,
    checked: Boolean,
    onToggle: () -> Unit,
    sourceName: String? = null,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) {},
        headlineContent = { Text(boardName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = sourceName?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
    )
}
