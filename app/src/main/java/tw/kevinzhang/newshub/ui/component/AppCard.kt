package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().then(modifier),
            onClick = onClick,
        ) {
            content()
        }
    } else {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().then(modifier),
        ) {
            content()
        }
    }
}
