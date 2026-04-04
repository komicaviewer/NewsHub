package tw.kevinzhang.newshub.ui.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.auth.LoginStatus
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateToMarketplace: () -> Unit,
    onLoginClick: (loginUrl: String, onPageLoadJs: String?) -> Unit = { _, _ -> },
    onLogoutClick: (loginUrl: String) -> Unit = {},
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loginStatuses by viewModel.loginStatuses.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
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
                        item(key = source.id) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(dimensionResource(R.dimen.space_8)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (source.requiresLogin && source.loginUrl != null) {
                                    val status = loginStatuses[source.loginUrl] ?: LoginStatus.NONE
                                    when (status) {
                                        LoginStatus.LOGGED_IN -> TextButton(
                                            onClick = { onLogoutClick(source.loginUrl!!) },
                                        ) { Text("Logout") }
                                        LoginStatus.FAILED -> TextButton(
                                            onClick = { onLoginClick(source.loginUrl!!, source.loginPageLoadJs) },
                                        ) { Text("Retry") }
                                        LoginStatus.NONE -> TextButton(
                                            onClick = { onLoginClick(source.loginUrl!!, source.loginPageLoadJs) },
                                        ) { Text("Login") }
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

    AppCard(onClick = { showSheet = true }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.space_8)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = board.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { showSheet = true }) { Text("Add") }
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))

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
                Text(
                    text = "加入 Collection",
                    style = MaterialTheme.typography.titleMedium,
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
                                .clickable {
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
