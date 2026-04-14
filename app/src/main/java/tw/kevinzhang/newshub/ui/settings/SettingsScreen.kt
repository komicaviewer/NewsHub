package tw.kevinzhang.newshub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
                headlineContent = {
                    Text(
                        "收藏貼文",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}
