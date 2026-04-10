package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.newshub.ui.component.AppCard
import tw.kevinzhang.newshub.ui.component.BodySmallText
import tw.kevinzhang.newshub.ui.component.TitleMediumText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = hiltViewModel(),
) {
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val repoUrls by viewModel.repoUrls.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onConfirm = { url ->
                viewModel.addRepo(url)
                showAddRepoDialog = false
            },
            onDismiss = { showAddRepoDialog = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRepoDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add repository")
            }
        },
    ) { innerPadding ->
        if (isLoading && extensions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(contentPadding = innerPadding) {
            if (repoUrls.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        TitleMediumText("Repositories")
                        repoUrls.forEach { url ->
                            RepoRow(url = url, onRemove = { viewModel.removeRepo(url) })
                        }
                    }
                }
            }

            if (extensions.isEmpty() && repoUrls.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Tap + to add an extension repository.")
                    }
                }
            } else {
                items(extensions, key = { it.first.id }) { (info, state) ->
                    ExtensionCard(info = info, state = state, onInstall = { viewModel.install(info) })
                }
            }
        }
    }
}

@Composable
private fun AddRepoDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Repository") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("GitHub repo URL") },
                placeholder = { Text("https://github.com/owner/extensions") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { if (url.isNotBlank()) onConfirm(url) }, enabled = url.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RepoRow(url: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BodySmallText(text = url, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove repository")
        }
    }
}

@Composable
private fun ExtensionCard(info: ExtensionInfo, state: InstallState, onInstall: () -> Unit) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TitleMediumText(text = info.name)
                BodySmallText(text = "${info.language} · v${info.versionName}")
            }
            when (state) {
                InstallState.NOT_INSTALLED -> Button(onClick = onInstall) { Text("Install") }
                InstallState.UPDATE_AVAILABLE -> Button(onClick = onInstall) { Text("Update") }
                InstallState.INSTALLED -> OutlinedButton(onClick = {}, enabled = false) { Text("Installed") }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
