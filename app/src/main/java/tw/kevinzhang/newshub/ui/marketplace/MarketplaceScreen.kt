package tw.kevinzhang.newshub.ui.marketplace

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
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel = hiltViewModel(),
) {
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val installError by viewModel.installError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(installError) {
        if (installError != null) {
            snackbarHostState.showSnackbar(installError!!)
            viewModel.clearInstallError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (isLoading && extensions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (extensions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No extensions available. Configure the marketplace URL in Settings.")
            }
            return@Scaffold
        }

        LazyColumn(contentPadding = innerPadding) {
            items(extensions, key = { it.first.id }) { (info, state) ->
                ExtensionCard(info = info, state = state, onInstall = { viewModel.install(info) })
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    info: ExtensionInfo,
    state: InstallState,
    onInstall: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.space_8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = info.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${info.language} · v${info.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state) {
                InstallState.NOT_INSTALLED -> Button(onClick = onInstall) { Text("Install") }
                InstallState.UPDATE_AVAILABLE -> Button(onClick = onInstall) { Text("Update") }
                InstallState.INSTALLED -> OutlinedButton(onClick = {}, enabled = false) { Text("Installed") }
            }
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_8)))
}
