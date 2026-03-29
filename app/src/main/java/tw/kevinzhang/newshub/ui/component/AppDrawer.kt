package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.collection.data.CollectionEntity
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.collection.CollectionsViewModel

@Composable
fun AppDrawer(
    onCollectionClick: (CollectionEntity) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle(emptyList())
    var showDialog by remember { mutableStateOf(false) }

    ModalDrawerSheet {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.collections),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Collection")
            }
        }
        HorizontalDivider()
        if (collections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No collections yet")
            }
        } else {
            LazyColumn {
                items(collections, key = { it.id }) { collection ->
                    NavigationDrawerItem(
                        label = { Text(collection.name) },
                        selected = false,
                        onClick = { onCollectionClick(collection) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Collection name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createCollection(name.trim())
                        showDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
