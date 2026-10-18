package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.newshub.github.GitHubRepo

@Composable
internal fun GitHubRepositoryPicker(onSelect: (GitHubRepo, RepositoryAccessCredential) -> Unit,
    onSessionChanged: () -> Unit, viewModel: GitHubRepositoryPickerViewModel = hiltViewModel(), selectionEnabled: Boolean = true) {
    val session by viewModel.session.state.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var expanded by remember(session.account) { mutableStateOf(true) }
    LaunchedEffect(session.account) { onSessionChanged(); if (session.account != null) viewModel.loadMore() }
    DisposableEffect(Unit) { onDispose { if (viewModel.session.state.value.busy) viewModel.cancel() } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GitHub 登入使用 repo 授權，GitHub 會一併授予寫入權限；NewsHub 僅讀取來源內容。",
            style = MaterialTheme.typography.bodySmall)
        if (!viewModel.session.configured) {
            Text("此版本尚未設定 GitHub 登入。", color = MaterialTheme.colorScheme.error)
        } else if (session.account == null) {
            if (!session.busy) Button(onClick = viewModel::login) { Text("登入 GitHub") }
        } else {
            Text("已登入 ${session.account!!.login}")
            TextButton(onClick = viewModel::logout) { Text("登出 GitHub") }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起 repository 清單" else "選擇其他 repository") }
            if (expanded) {
            OutlinedTextField(value = search.query, onValueChange = viewModel::query,
                label = { Text("搜尋可讀取的 repositories") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("清單受組織 OAuth／SSO 政策限制。搜尋時會繼續讀取後續頁面。", style = MaterialTheme.typography.bodySmall)
            // Parent is a LazyColumn: cap this batch and offer more locally to avoid composing a huge directory.
            var visible by remember(search.query) { mutableIntStateOf(20) }
            search.results.take(visible).forEach { repo ->
                OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = selectionEnabled, onClick = {
                    viewModel.session.currentCredential()?.let { onSelect(repo, it); expanded = false }
                }) { Column(Modifier.fillMaxWidth()) {
                    Text(repo.fullName)
                    Text(if (repo.private) "私人 repository" else "公開 repository", style = MaterialTheme.typography.bodySmall)
                    repo.description?.let { Text(it, maxLines = 2, style = MaterialTheme.typography.bodySmall) }
                } }
            }
            if (search.results.size > visible) TextButton(onClick = { visible += 20 }) { Text("顯示更多結果") }
            if (search.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在搜尋／讀取目錄…") }
            else if (!search.complete) TextButton(onClick = viewModel::loadMore) { Text("載入更多／重試") }
            if (search.complete && search.results.isEmpty()) Text("沒有符合的 repository。")
            search.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (session.busy) {
            session.userCode?.let { code ->
                Text("請在 GitHub 輸入授權碼：$code", style = MaterialTheme.typography.titleMedium)
                Text("授權碼會自動到期；授權完成後此畫面會繼續。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { session.verificationUri?.let(uriHandler::openUri) }) { Text("開啟 GitHub 授權頁") }
            } ?: CircularProgressIndicator()
            TextButton(onClick = viewModel::cancel) { Text("取消登入") }
        }
        session.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { uriHandler.openUri("https://github.com/settings/applications") }) { Text("管理 GitHub 授權") }
    }
}
