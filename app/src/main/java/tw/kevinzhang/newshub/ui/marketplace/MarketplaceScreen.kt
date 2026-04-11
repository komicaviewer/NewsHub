package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tw.kevinzhang.marketplace.data.AvailableSource
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.InstallStep
import tw.kevinzhang.newshub.ui.component.TitleMediumText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MarketplaceScreen(
    onNavigateUp: () -> Unit,
    onNavigateToManageRepos: () -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel(),
) {
    val repoGroups by viewModel.repoGroups.collectAsStateWithLifecycle()
    val installSteps by viewModel.installSteps.collectAsStateWithLifecycle()
    val installedPackageNames by viewModel.installedPackageNames.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedInfo by remember { mutableStateOf<ExtensionInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marketplace") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToManageRepos) {
                        Text("管理來源")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->

        selectedInfo?.let { info ->
            val installState = remember(info.id, installedPackageNames) { viewModel.getInstallState(info) }
            val step = installSteps[info.id] ?: InstallStep.IDLE
            ModalBottomSheet(
                onDismissRequest = { selectedInfo = null },
                sheetState = sheetState,
            ) {
                ExtensionDetailSheet(
                    info = info,
                    installState = installState,
                    installStep = step,
                    onInstall = { viewModel.install(info) },
                    onUninstall = { viewModel.uninstall(info.id) },
                )
            }
        }

        if (isLoading && repoGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (repoGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "尚無擴充套件來源",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onNavigateToManageRepos) {
                        Text("新增來源")
                    }
                }
            }
            return@Scaffold
        }

        LazyColumn(contentPadding = innerPadding) {
            repoGroups.forEach { group ->
                stickyHeader(key = "header_${group.repoUrl}") {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (group.metadata.iconUrl != null) {
                                    AsyncImage(
                                        model = group.metadata.iconUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                TitleMediumText(
                                    text = group.metadata.name,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Box {}
                        }
                    }
                }
                items(group.extensions, key = { it.id }) { info ->
                    val step = installSteps[info.id] ?: InstallStep.IDLE
                    val installState = remember(info.id, installedPackageNames) { viewModel.getInstallState(info) }
                    ExtensionListItem(
                        info = info,
                        installState = installState,
                        installStep = step,
                        onInstall = { viewModel.install(info) },
                        onUninstall = { viewModel.uninstall(info.id) },
                        onClick = { selectedInfo = info },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun ExtensionListItem(
    info: ExtensionInfo,
    installState: InstallState,
    installStep: InstallStep,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    val isInProgress = installStep == InstallStep.DOWNLOADING ||
            installStep == InstallStep.INSTALLING ||
            installStep == InstallStep.PENDING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Extension icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (info.iconUrl != null) {
                AsyncImage(
                    model = info.iconUrl,
                    contentDescription = info.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = info.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Name + metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${info.sources.size} source(s) · ${info.language} · v${info.versionName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Action
        when {
            isInProgress -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )

            installState == InstallState.INSTALLED -> TextButton(
                onClick = onUninstall,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("解除")
            }

            installState == InstallState.UPDATE_AVAILABLE -> FilledTonalButton(onClick = onInstall) {
                Text("更新")
            }

            else -> FilledTonalButton(onClick = onInstall) {
                Text("安裝")
            }
        }
    }
}

@Composable
private fun ExtensionDetailSheet(
    info: ExtensionInfo,
    installState: InstallState,
    installStep: InstallStep,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val isInProgress = installStep == InstallStep.DOWNLOADING ||
            installStep == InstallStep.INSTALLING ||
            installStep == InstallStep.PENDING

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Large icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (info.iconUrl != null) {
                AsyncImage(
                    model = info.iconUrl,
                    contentDescription = info.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = info.name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = info.name,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = info.id,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Metadata rows
        DetailRow(label = "版本", value = "${info.versionName} (${info.version})")
        DetailRow(label = "語言", value = info.language)

        if (info.sources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "包含來源（${info.sources.size}）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            info.sources.forEach { source ->
                SourceRow(source = source)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button — full width
        when {
            isInProgress -> CircularProgressIndicator()
            installState == InstallState.INSTALLED -> Button(
                onClick = onUninstall,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text("解除安裝")
            }
            installState == InstallState.UPDATE_AVAILABLE -> Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("更新")
            }
            else -> Button(
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("安裝")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SourceRow(source: AvailableSource) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = source.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

