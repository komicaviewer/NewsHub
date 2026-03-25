package tw.kevinzhang.newshub.ui.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard

@Composable
fun ExtensionsScreen(
    onNavigateToMarketplace: () -> Unit,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Marketplace entry point
        Button(
            onClick = onNavigateToMarketplace,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.space_8)),
        ) {
            Text("Browse Marketplace")
        }

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
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(dimensionResource(R.dimen.space_8)),
                        )
                    }
                    items(boards, key = { "${source.id}:${it.url}" }) { board ->
                        BoardRow(
                            board = board,
                            collections = collections,
                            onAddToCollection = { collectionId ->
                                viewModel.addBoardToCollection(collectionId, board, source)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(
    board: Board,
    collections: List<CollectionEntity>,
    onAddToCollection: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    AppCard(onClick = { showDialog = true }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.space_8)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = board.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { showDialog = true }) { Text("Add") }
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Add to Collection") },
            text = {
                if (collections.isEmpty()) {
                    Text("No collections yet. Create one first.")
                } else {
                    LazyColumn {
                        items(collections, key = { it.id }) { collection ->
                            TextButton(
                                onClick = {
                                    onAddToCollection(collection.id)
                                    showDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(collection.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
