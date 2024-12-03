package tw.kevinzhang.newshub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun NewshubTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ColorPalette,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}