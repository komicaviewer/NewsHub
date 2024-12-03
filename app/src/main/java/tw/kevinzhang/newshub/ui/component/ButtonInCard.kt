package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ButtonInCard(
    onClick: (() -> Unit)? = null,
    painter: Painter,
) {
    IconButton(
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
        modifier = Modifier.size(16.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null
        )
    }
}

@Composable
fun ButtonInCard(
    onClick: (() -> Unit)? = null,
    resource: Int,
) {
    ButtonInCard(
        onClick = onClick,
        painter = painterResource(resource),
    )
}

@Composable
fun ButtonInCard(
    onClick: (() -> Unit)? = null,
    imageVector: ImageVector,
) {
    ButtonInCard(
        onClick = onClick,
        painter = rememberVectorPainter(imageVector),
    )
}