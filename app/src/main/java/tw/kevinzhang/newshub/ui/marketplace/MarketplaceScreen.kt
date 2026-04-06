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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val installError by viewModel.installError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(installError) {
        val error = installError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearInstallError()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        if (isLoading && extensions.isEmpty()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (extensions.isEmpty()) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = Alignment.Center) {
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
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TitleMediumText(text = info.name)
                BodySmallText(
                    text = "${info.language} · v${info.versionName}",
                )
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
