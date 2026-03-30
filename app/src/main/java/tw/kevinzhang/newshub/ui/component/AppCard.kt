package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import tw.kevinzhang.newshub.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            tonalElevation = dimensionResource(id = R.dimen.space_2),
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            content()
        }
    } else {
        Surface(
            tonalElevation = dimensionResource(id = R.dimen.space_2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}