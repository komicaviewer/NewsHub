package tw.kevinzhang.newshub.ui.topic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import tw.kevinzhang.newshub.ui.component.NewsHubTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyRoute(
    openDrawer: () -> Unit,
    title: String = "Empty",
){
    Scaffold(
        topBar = {
            NewsHubTopBar(
                onMenuPressed = { openDrawer() },
                title = title,
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(it),
        ) {
        }
    }
}