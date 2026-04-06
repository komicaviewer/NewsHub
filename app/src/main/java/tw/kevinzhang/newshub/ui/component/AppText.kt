package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    val paddingModifier = Modifier.padding(
        start = paddingStart ?: paddingHorizontal ?: padding ?: 0.dp,
        top = paddingTop ?: paddingVertical ?: padding ?: 0.dp,
        end = paddingEnd ?: paddingHorizontal ?: padding ?: 0.dp,
        bottom = paddingBottom ?: paddingVertical ?: padding ?: 0.dp
    )

    Text(
        text = text,
        style = style,
        modifier = modifier.then(paddingModifier),
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun BodySmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun BodyMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun BodyLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun TitleSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun TitleMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun TitleLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun LabelSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun LabelMediumText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun LabelLargeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}

@Composable
fun HeadlineSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    padding: Dp? = null,
    paddingHorizontal: Dp? = null,
    paddingVertical: Dp? = null,
    paddingStart: Dp? = null,
    paddingTop: Dp? = null,
    paddingEnd: Dp? = null,
    paddingBottom: Dp? = null,
) {
    AppText(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        padding = padding,
        paddingHorizontal = paddingHorizontal,
        paddingVertical = paddingVertical,
        paddingStart = paddingStart,
        paddingTop = paddingTop,
        paddingEnd = paddingEnd,
        paddingBottom = paddingBottom,
    )
}
