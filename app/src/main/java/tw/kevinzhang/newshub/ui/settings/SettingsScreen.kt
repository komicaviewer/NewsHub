package tw.kevinzhang.newshub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tw.kevinzhang.newshub.ui.component.appClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToReadingHistory: () -> Unit,
    onNavigateToSavedPosts: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("設定") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("閱讀紀錄") },
                leadingContent = {
                    Icon(Icons.Outlined.History, contentDescription = null)
                },
                modifier = Modifier.appClickable(onClick = onNavigateToReadingHistory),
            )
            ListItem(
                headlineContent = { Text("收藏貼文") },
                leadingContent = {
                    Icon(Icons.Outlined.Bookmark, contentDescription = null)
                },
                modifier = Modifier.appClickable(onClick = onNavigateToSavedPosts),
            )
        }
    }
}
