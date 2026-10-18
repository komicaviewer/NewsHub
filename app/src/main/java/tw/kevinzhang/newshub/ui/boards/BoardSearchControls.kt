package tw.kevinzhang.newshub.ui.boards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import tw.kevinzhang.extension_api.model.*

internal fun boardSearchEmptyText(query: String) = if (query.isBlank()) "此來源目前沒有推薦看板" else "找不到符合的看板"

internal fun boardCatalogStatusText(info: BoardCatalogInfo?): String {
    if (info == null) return "來源未提供目錄更新資訊"
    val parts = mutableListOf<String>()
    if (info.coverage == BoardCatalogCoverage.PARTIAL) parts += "目前僅取得部分看板，可更新重試"
    if (info.coverage == BoardCatalogCoverage.UNKNOWN) parts += "來源未確認目錄涵蓋範圍"
    if (info.isStale) parts += "更新失敗，顯示舊資料"
    info.updatedAtMillis?.let { parts += "資料更新於 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" }
    return parts.joinToString(" · ")
}

@Composable
internal fun BoardSearchControls(query: String, loading: Boolean, info: BoardCatalogInfo?, onQuery: (String) -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("搜尋此論壇看板…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Outlined.Close, contentDescription = "清除搜尋")
            } },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (query.isBlank()) "推薦看板" else "搜尋結果", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onRefresh, enabled = !loading) { Text("更新目錄") }
        }
        Text(boardCatalogStatusText(info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
