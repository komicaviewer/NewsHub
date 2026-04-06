package tw.kevinzhang.newshub.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tw.kevinzhang.newshub.ui.theme.NewshubTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsHubTopBar(
    title: String = "",
    onMenuPressed: (() -> Unit)? = null,
    onBackPressed: (() -> Unit)? = null,
) {
    SmallTopAppBar(
        title = {
            TitleLargeText(
                text = title,
            )
        },
        navigationIcon = {
            if (onBackPressed != null) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            } else if (onMenuPressed != null) {
                IconButton(onClick = onMenuPressed) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Menu",
                    )
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewNewsHubTopBar() {
    NewshubTheme {
        NewsHubTopBar("NewsHub")
    }
}

@Preview
@Composable
private fun PreviewNewsHubTopBarWithBack() {
    NewshubTheme {
        NewsHubTopBar(
            title = "NewsHub",
            onBackPressed = { },
        )
    }
}

@Preview
@Composable
private fun PreviewNewsHubTopBarWithMenu() {
    NewshubTheme {
        NewsHubTopBar(
            title = "NewsHub",
            onMenuPressed = { },
        )
    }
}