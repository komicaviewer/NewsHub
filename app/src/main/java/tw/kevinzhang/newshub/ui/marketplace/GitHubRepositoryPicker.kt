package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.newshub.github.GitHubRepo

@Composable
internal fun GitHubRepositoryPicker(onSelect: (GitHubRepo, RepositoryAccessCredential) -> Unit,
    onSessionChanged: () -> Unit, viewModel: GitHubRepositoryPickerViewModel = hiltViewModel(), selectionEnabled: Boolean = true, showRepositories: Boolean = true) {
    val session by viewModel.session.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val loginScroll = rememberScrollState()
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(session.account) { onSessionChanged(); if (session.account != null) viewModel.loadMore() }
    DisposableEffect(Unit) { onDispose { if (viewModel.session.state.value.busy) viewModel.cancel() } }
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight().then(
        if (session.account == null) Modifier.verticalScroll(loginScroll) else Modifier
    ), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!viewModel.session.configured) {
            Text("此版本尚未設定 GitHub 登入。", color = MaterialTheme.colorScheme.error)
        } else if (session.account == null) {
            Text("連接 GitHub", style = MaterialTheme.typography.headlineMedium)
            Text("選擇你能存取的擴充來源。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("GitHub 此授權包含儲存庫寫入權限；NewsHub 僅用於讀取來源。",
                style = MaterialTheme.typography.bodyMedium)
            if (!session.busy) Button(onClick = viewModel::login, modifier = Modifier.fillMaxWidth()) {
                Text("繼續連接 GitHub")
            }
        } else if (showRepositories) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(session.account!!.login, style = MaterialTheme.typography.titleMedium)
                    Text("GitHub 已連接", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "帳號選單")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("管理 GitHub 授權") }, onClick = {
                            menuOpen = false; uriHandler.openUri("https://github.com/settings/applications")
                        })
                        DropdownMenuItem(text = { Text("登出 GitHub") }, onClick = {
                            menuOpen = false; viewModel.logout()
                        })
                    }
                }
            }
            OutlinedTextField(value = search.query, onValueChange = viewModel::query,
                label = { Text("搜尋儲存庫") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (search.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在讀取更多儲存庫…", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(search.results, key = { it.fullName }) { repo ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = selectionEnabled) {
                        viewModel.session.currentCredential()?.let { onSelect(repo, it) }
                    }.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(repo.fullName, style = MaterialTheme.typography.titleSmall)
                        Text(if (repo.private) "私人儲存庫" else "公開儲存庫",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        repo.description?.let {
                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    if (!search.loading && !search.complete) TextButton(onClick = viewModel::loadMore) { Text("載入更多儲存庫") }
                    if (search.complete && search.results.isEmpty()) Text("沒有符合的儲存庫。", Modifier.padding(vertical = 24.dp))
                    search.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("找不到組織的儲存庫？請確認組織的 OAuth／SSO 授權。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
        }
        if (session.busy) {
            session.userCode?.let { code ->
                var copied by remember(code) { mutableStateOf(false) }
                Text("請在 GitHub 輸入授權碼：", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(code, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }) { Text(if (copied) "已複製" else "複製授權碼") }
                }
                Text("授權碼會自動到期；授權完成後此畫面會繼續。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { session.verificationUri?.let(uriHandler::openUri) }) { Text("開啟 GitHub 授權頁") }
            } ?: CircularProgressIndicator()
            TextButton(onClick = viewModel::cancel) { Text("取消登入") }
        }
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    }
}
