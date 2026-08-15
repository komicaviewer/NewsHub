package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryRootPreview
import tw.kevinzhang.marketplace.RepositoryTrustDomain
import tw.kevinzhang.marketplace.RepositoryTrustMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageReposScreen(
    onNavigateUp: () -> Unit,
    viewModel: ManageReposViewModel = hiltViewModel(),
) {
    val domains by viewModel.repositoryDomains.collectAsStateWithLifecycle()
    val addRepoUrl by viewModel.addRepoUrl.collectAsStateWithLifecycle()
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()
    var revokeCandidate by remember { mutableStateOf<RepositoryTrustDomain?>(null) }

    revokeCandidate?.let { domain ->
        AlertDialog(
            onDismissRequest = { revokeCandidate = null },
            title = { Text("撤銷這個來源的信任？") },
            text = { Text("已安裝的套件會保留，但會立即停用，也無法再恢復這個信任紀錄。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeRepository(domain.id)
                    revokeCandidate = null
                }) { Text("撤銷信任") }
            },
            dismissButton = { TextButton(onClick = { revokeCandidate = null }) { Text("取消") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理來源") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                AddRepoSection(
                    url = addRepoUrl,
                    state = validationState,
                    onUrlChanged = viewModel::onAddRepoUrlChanged,
                    onInspect = viewModel::inspectNow,
                    onConfirm = viewModel::confirmTrust,
                    onCancel = viewModel::cancelTrustConfirmation,
                )
            }
            item {
                HorizontalDivider()
                Text(
                    "已授權的來源",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(domains, key = RepositoryTrustDomain::id) { domain ->
                RepositoryDomainItem(
                    domain = domain,
                    onSuspend = { viewModel.suspendRepository(domain.id) },
                    onResume = { viewModel.resumeRepository(domain.id) },
                    onRevoke = { revokeCandidate = domain },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun AddRepoSection(
    url: String,
    state: AddRepoValidationState,
    onUrlChanged: (String) -> Unit,
    onInspect: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy = state is AddRepoValidationState.InspectingRoot || state is AddRepoValidationState.Confirming
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("新增 HTTPS 來源", style = MaterialTheme.typography.titleMedium)
        Text(
            "NewsHub 會先顯示來源的金鑰指紋，只有你確認後才會建立信任。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text("固定 HTTPS 網址") },
            placeholder = { Text("https://example.org/extensions") },
            enabled = !busy,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when (state) {
            AddRepoValidationState.Idle -> Button(
                onClick = onInspect,
                enabled = url.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) { Text("檢查金鑰") }
            AddRepoValidationState.InspectingRoot -> BusyMessage("正在安全地讀取根金鑰…")
            is AddRepoValidationState.Confirming -> BusyMessage("正在驗證並加入來源…")
            is AddRepoValidationState.AwaitingTrustConfirmation -> TrustPreview(
                state.preview,
                onConfirm,
                onCancel,
            )
            is AddRepoValidationState.Error -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            is AddRepoValidationState.Success -> Text(
                "已安全加入 ${state.domain.canonicalBaseUrl}",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BusyMessage(text: String) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(text)
}

@Composable
private fun TrustPreview(
    preview: RepositoryRootPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("確認首次信任（TOFU）", style = MaterialTheme.typography.titleSmall)
        Text(
            "首次取得的金鑰可能遭到網路冒用。建議從發布者的其他可信管道核對下列指紋。",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Text("根簽章門檻：${preview.rootThreshold} 把金鑰")
        preview.rootKeyFingerprints.sorted().forEach { fingerprint ->
            Text(
                fingerprint.chunked(8).joinToString(" "),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onCancel) { Text("取消") }
            Button(onClick = onConfirm) { Text("我已核對，信任並加入") }
        }
    }
}

@Composable
private fun RepositoryDomainItem(
    domain: RepositoryTrustDomain,
    onSuspend: () -> Unit,
    onResume: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            domain.canonicalBaseUrl,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${domain.trustModeLabel()} · ${domain.stateLabel()} · 根門檻 ${domain.rootThreshold}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "金鑰 ${domain.rootKeyFingerprints.sorted().joinToString { it.take(12) + "…" }}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (domain.trustMode == RepositoryTrustMode.USER_PINNED && domain.state != RepositoryDomainState.REVOKED) {
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                if (domain.state == RepositoryDomainState.ACTIVE) {
                    OutlinedButton(onClick = onSuspend) { Text("暫停") }
                } else {
                    OutlinedButton(onClick = onResume) { Text("恢復") }
                }
                TextButton(onClick = onRevoke) { Text("撤銷") }
            }
        }
    }
}

internal fun RepositoryTrustDomain.trustModeLabel(): String = when (trustMode) {
    RepositoryTrustMode.BUILTIN_PINNED -> "內建信任"
    RepositoryTrustMode.USER_PINNED -> "使用者信任"
}

internal fun RepositoryTrustDomain.stateLabel(): String = when (state) {
    RepositoryDomainState.ACTIVE -> "正常"
    RepositoryDomainState.EXPIRED -> "已過期"
    RepositoryDomainState.SUSPENDED -> "已暫停"
    RepositoryDomainState.REVOKED -> "已撤銷"
}
