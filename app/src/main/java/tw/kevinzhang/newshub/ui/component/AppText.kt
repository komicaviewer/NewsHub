package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph

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


@Composable
fun Paragraph.Text.View() {
    BodyMediumText(text = content)
}

@Composable
fun Paragraph.Text.Small() {
    BodySmallText(text = content)
}

@Composable
fun Paragraph.Quote.Small() {
    BodySmallText(
        text = "> $content",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Paragraph.ReplyTo.View(onReplyToClick: ((String) -> Unit)? = null) {
    if (onReplyToClick == null) {
        if (preview == null) {
            BodyMediumText(text = ">> $targetId")
        } else {
            BodyMediumText(text = ">> ${targetId}(${preview})")
        }
    } else {
        TextButton(
            onClick = { onReplyToClick(targetId) },
            contentPadding = PaddingValues(0.dp),
        ) {
            if (preview == null) {
                Text(">> $targetId")
            } else {
                Text(">> ${targetId}(${preview})")
            }
        }
    }
}

@Composable
fun Paragraph.ReplyTo.Small(onReplyToClick: ((String) -> Unit)? = null) {
    if (onReplyToClick == null) {
        if (preview == null) {
            BodySmallText(text = ">> $targetId")
        } else {
            BodySmallText(text = ">> ${targetId}(${preview})")
        }
    } else {
        TextButton(
            onClick = { onReplyToClick(targetId) },
            contentPadding = PaddingValues(0.dp),
        ) {
            if (preview == null) {
                Text(">> $targetId")
            } else {
                Text(">> ${targetId}(${preview})")
            }
        }
    }
}

@Composable
fun Paragraph.Link.View() {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = {
            uriHandler.openUri(content)
        },
        contentPadding = PaddingValues(0.dp),
        shape = RectangleShape,
    ) { Text(content) }
}

@Composable
fun Paragraph.Link.Small() {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = {
            uriHandler.openUri(content)
        },
        contentPadding = PaddingValues(0.dp),
        shape = RectangleShape,
    ) { BodySmallText(content) }
}

@Composable
fun Paragraph.ImageInfo.View(
    alwaysUseRawImage: Boolean,
    onClick: (() -> Unit)? = null
) {
    val url =
        if (alwaysUseRawImage) raw else thumb
    var m = Modifier.fillMaxWidth()
    if (onClick != null) {
        m = m.clickable { onClick() }
    }
    url?.let { AsyncImage(model = it, modifier = m, contentDescription = null) }
}


@Composable
fun Paragraph.VideoInfo.View(onClick: (() -> Unit)? = null) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) {
        m = m.clickable { onClick() }
    }

    Box(
        modifier = m,
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )
        Icon(
            imageVector = Icons.Default.PlayCircle,
            contentDescription = "播放影片",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(48.dp),
        )
    }
}
