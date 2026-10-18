package tw.kevinzhang.newshub.ui.marketplace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.marketplace.RepositoryDomainState
import tw.kevinzhang.marketplace.RepositoryAccessKind
import tw.kevinzhang.marketplace.RepositoryCredentialBinding
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
    val accessKind by viewModel.accessKind.collectAsStateWithLifecycle()
    val accountMode by viewModel.githubAccountMode.collectAsStateWithLifecycle()
    val revision by viewModel.repositoryRevision.collectAsStateWithLifecycle()
    val directory by viewModel.repositoryDirectory.collectAsStateWithLifecycle()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
    val validationState by viewModel.validationState.collectAsStateWithLifecycle()
    val accessRequiredDomainIds by viewModel.accessRequiredDomainIds.collectAsStateWithLifecycle()
    val reauthorizationState by viewModel.reauthorizationState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(true) }
    val busy = validationState is AddRepoValidationState.InspectingRoot || validationState is AddRepoValidationState.Confirming
    val back: () -> Unit = {
        if (adding && !busy) {
            if (accountMode && !choosing) { viewModel.cancelTrustConfirmation(); choosing = true }
            else { viewModel.cancelTrustConfirmation(); adding = false }
        } else if (!adding) onNavigateUp()
    }
    BackHandler(enabled = adding) { back() }
    var revokeCandidate by remember { mutableStateOf<RepositoryTrustDomain?>(null) }

    when (val reauth = reauthorizationState) {
        RepositoryReauthorizationState.Idle -> Unit
        is RepositoryReauthorizationState.Editing -> ReauthorizationDialog(
            token = reauth.token,
            errorMessage = reauth.errorMessage,
            isVerifying = false,
            onTokenChanged = viewModel::onReplacementTokenChanged,
            onConfirm = viewModel::confirmReauthorization,
            onDismiss = viewModel::cancelReauthorization,
        )
        is RepositoryReauthorizationState.Verifying -> ReauthorizationDialog(
            token = "",
            errorMessage = null,
            isVerifying = true,
            onTokenChanged = {},
            onConfirm = {},
            onDismiss = {},
        )
    }

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
                title = { Text(if (!adding) "管理來源" else if (!accountMode) "新增來源" else if (choosing) "選擇 GitHub 來源" else "檢查來源") },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        if (adding && accountMode) {
            Box(if (choosing) Modifier.widthIn(max = 640.dp).weight(1f).padding(horizontal = 24.dp, vertical = 12.dp) else Modifier.height(0.dp)) {
                GitHubRepositoryPicker(
                    onSelect = { repo, credential -> viewModel.selectGitHubRepository(repo, credential); choosing = false },
                    onSessionChanged = { viewModel.invalidateGitHubSelection(); choosing = true },
                    selectionEnabled = !busy,
                    showRepositories = choosing,
                )
            }
        }
        if (!(adding && accountMode && choosing)) LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            if (!adding) item {
                Button(onClick = { adding = true; choosing = true; viewModel.onAccessKindChanged(RepositoryAccessKind.PUBLIC_HTTPS) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("新增來源") }
            }
            if (adding) item {
                AddRepoSection(
                    url = addRepoUrl,
                    accessKind = accessKind,
                    accountMode = accountMode,
                    revision = revision,
                    directory = directory,
                    onAccountMode = { choosing = true; viewModel.useGitHubAccount() },
                    onRevisionChanged = viewModel::onRepositoryRevisionChanged,
                    onDirectoryChanged = viewModel::onRepositoryDirectoryChanged,
                    githubToken = githubToken,
                    state = validationState,
                    onUrlChanged = viewModel::onAddRepoUrlChanged,
                    onAccessKindChanged = viewModel::onAccessKindChanged,
                    onGithubTokenChanged = viewModel::onGithubTokenChanged,
                    onInspect = viewModel::inspectNow,
                    onConfirm = viewModel::confirmTrust,
                    onCancel = viewModel::cancelTrustConfirmation,
                )
                if (validationState is AddRepoValidationState.Success) {
                    Button(onClick = { viewModel.cancelTrustConfirmation(); adding = false },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("完成") }
                }
            }
            if (!adding) item {
                HorizontalDivider()
                Text(
                    "已授權的來源",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            if (!adding) items(domains, key = RepositoryTrustDomain::id) { domain ->
                RepositoryDomainItem(
                    domain = domain,
                    onSuspend = { viewModel.suspendRepository(domain.id) },
                    onResume = { viewModel.resumeRepository(domain.id) },
                    onRevoke = { revokeCandidate = domain },
                    accessRequired = domain.id in accessRequiredDomainIds,
                    onReauthorize = { viewModel.beginReauthorization(domain.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddRepoSection(
    url: String,
    accessKind: RepositoryAccessKind,
    accountMode: Boolean,
    revision: String,
    directory: String,
    onAccountMode: () -> Unit,
    onRevisionChanged: (String) -> Unit,
    onDirectoryChanged: (String) -> Unit,
    githubToken: String,
    state: AddRepoValidationState,
    onUrlChanged: (String) -> Unit,
    onAccessKindChanged: (RepositoryAccessKind) -> Unit,
    onGithubTokenChanged: (String) -> Unit,
    onInspect: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val busy = state is AddRepoValidationState.InspectingRoot || state is AddRepoValidationState.Confirming
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!accountMode) Text("選擇加入方式", style = MaterialTheme.typography.titleLarge)
        if (!accountMode) Text(
            "加入前會檢查來源格式，並請你核對簽署金鑰。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!accountMode) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = accessKind == RepositoryAccessKind.PUBLIC_HTTPS,
                onClick = { onAccessKindChanged(RepositoryAccessKind.PUBLIC_HTTPS) },
                label = { Text("公開 HTTPS") },
                enabled = !busy,
            )
            FilterChip(
                selected = accessKind == RepositoryAccessKind.GITHUB_CONTENTS && !accountMode,
                onClick = { onAccessKindChanged(RepositoryAccessKind.GITHUB_CONTENTS) },
                label = { Text("GitHub private") },
                enabled = !busy,
            )
            FilterChip(selected = accountMode, onClick = onAccountMode, label = { Text("GitHub 帳號") }, enabled = !busy)
        }
        if (accountMode) {
            Text(url.removePrefix("https://github.com/"), style = MaterialTheme.typography.titleLarge)
            Text("確認來源位置，再檢查簽署金鑰。", style = MaterialTheme.typography.bodyMedium)
        }
        if (!accountMode) OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text(if (accessKind == RepositoryAccessKind.GITHUB_CONTENTS) "GitHub repository URL" else "固定 HTTPS 網址") },
            placeholder = {
                Text(
                    if (accessKind == RepositoryAccessKind.GITHUB_CONTENTS) {
                        "https://github.com/owner/private-repo"
                    } else {
                        "https://example.org/extensions"
                    },
                )
            },
            enabled = !busy && !accountMode,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (accessKind == RepositoryAccessKind.GITHUB_CONTENTS && !accountMode) {
            OutlinedTextField(
                value = githubToken,
                onValueChange = onGithubTokenChanged,
                label = { Text("Fine-grained personal access token") },
                supportingText = { Text("建議僅授予此 repository 的 Contents: read 權限。") },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !busy,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        var advanced by remember(url) { mutableStateOf(false) }
        if (accessKind == RepositoryAccessKind.GITHUB_CONTENTS && url.isNotBlank()) {
            TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text(if (advanced) "收起進階設定" else "進階設定：分支與子目錄") }
        }
        if (accessKind == RepositoryAccessKind.GITHUB_CONTENTS && url.isNotBlank() && advanced) {
            OutlinedTextField(value = revision, onValueChange = onRevisionChanged, label = { Text("分支或 revision") },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = directory, onValueChange = onDirectoryChanged, label = { Text("子目錄（空白表示根目錄）") },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        when (state) {
            AddRepoValidationState.Idle -> Button(
                onClick = onInspect,
                enabled = url.isNotBlank() && (
                    accessKind == RepositoryAccessKind.PUBLIC_HTTPS || accountMode || githubToken.isNotBlank()
                ),
                modifier = Modifier.align(Alignment.End),
            ) { Text("檢查來源") }
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
            is AddRepoValidationState.AccessRequired -> Row(
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
        if (state is AddRepoValidationState.Error || state is AddRepoValidationState.AccessRequired) {
            TextButton(onClick = onInspect, enabled = url.isNotBlank() && (accountMode ||
                accessKind == RepositoryAccessKind.PUBLIC_HTTPS || githubToken.isNotBlank())) { Text("重新檢查") }
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
    accessRequired: Boolean,
    onReauthorize: () -> Unit,
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
            "${domain.accessLabel()} · ${domain.trustModeLabel()} · ${domain.stateLabel()} · 根門檻 ${domain.rootThreshold}",
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
        if (accessRequired) {
            Text(
                if (domain.access.credentialBinding == RepositoryCredentialBinding.GITHUB_ACCOUNT)
                    "GitHub 更新已暫停或待確認，請使用「登入 GitHub」確認帳號及讀取權限。"
                else "需要重新授權才能更新 metadata 或下載 APK。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (domain.trustMode == RepositoryTrustMode.USER_PINNED && domain.state != RepositoryDomainState.REVOKED) {
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                if (domain.access.credentialBinding == RepositoryCredentialBinding.MANUAL_PAT) {
                    TextButton(onClick = onReauthorize) {
                        Text(if (accessRequired) "重新授權" else "更新 token")
                    }
                }
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

@Composable
private fun ReauthorizationDialog(
    token: String,
    errorMessage: String?,
    isVerifying: Boolean,
    onTokenChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { Text("更新 GitHub 存取權") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("新的 token 會先通過 repository 與既有根信任驗證，成功後才取代舊 token。")
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChanged,
                    label = { Text("Fine-grained personal access token") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isVerifying,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isVerifying) BusyMessage("正在驗證存取權…")
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isVerifying && token.isNotBlank()) {
                Text("驗證並更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isVerifying) { Text("取消") }
        },
    )
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

internal fun RepositoryTrustDomain.accessLabel(): String = when (access.kind) {
    RepositoryAccessKind.PUBLIC_HTTPS -> "公開 HTTPS"
    RepositoryAccessKind.GITHUB_CONTENTS -> if (access.credentialBinding == RepositoryCredentialBinding.GITHUB_ACCOUNT)
        "GitHub 帳號 (${access.revision}${access.repositoryPath.takeIf { it.isNotEmpty() }?.let { ":$it" }.orEmpty()})"
        else "GitHub private (${access.revision})"
}
