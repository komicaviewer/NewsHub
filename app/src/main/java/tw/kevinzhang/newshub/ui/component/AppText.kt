package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.ExternalLinkHandle
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.RichTextColor
import tw.kevinzhang.extension_api.model.RichTextEmphasis
import tw.kevinzhang.extension_api.model.RichTextLayout
import tw.kevinzhang.newshub.ui.component.gallery.VideoPlayer
import tw.kevinzhang.newshub.auth.hostResourceProvider

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
fun Paragraph.RichText.View() {
    RichTextParagraph(
        runs = runs,
        layout = layout,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun Paragraph.RichText.Small() {
    RichTextParagraph(
        runs = runs,
        layout = layout,
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * Renders extension supplied semantic rich text without changing the surrounding post layout.
 * [RichTextLayout.PREFORMATTED_WRAP] preserves the source's whitespace/newlines in a monospaced
 * face, while Compose's soft wrapping keeps it readable on a phone screen.
 */
@Composable
private fun RichTextParagraph(
    runs: List<tw.kevinzhang.extension_api.model.RichTextRun>,
    layout: RichTextLayout,
    style: TextStyle,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val resourceProvider = remember(context) { context.hostResourceProvider() }
    val text = buildAnnotatedString {
        runs.forEach { run ->
            val start = length
            append(run.text)
            val end = length
            if (start != end) {
                addStyle(
                    style = SpanStyle(
                        color = run.color.resolveForTheme(isDark, run.emphasis),
                        fontWeight = if (run.emphasis == RichTextEmphasis.BRIGHT) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    ),
                    start = start,
                    end = end,
                )
                run.linkUrl?.takeIf(String::isNotBlank)?.let { url ->
                    addStringAnnotation(tag = RICH_TEXT_URL_TAG, annotation = url, start = start, end = end)
                }
            }
        }
    }
    ClickableText(
        text = text,
        style = if (layout == RichTextLayout.PREFORMATTED_WRAP) {
            style.copy(fontFamily = FontFamily.Monospace)
        } else {
            style
        },
        softWrap = true,
        onClick = { offset ->
            text.getStringAnnotations(RICH_TEXT_URL_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.let { openExternalLink(it, resourceProvider::consumeExternalLink, uriHandler::openUri) }
        },
    )
}

private const val RICH_TEXT_URL_TAG = "rich_text_url"

private fun RichTextColor.resolveForTheme(
    isDark: Boolean,
    emphasis: RichTextEmphasis,
): Color {
    val normal = if (isDark) {
        when (this) {
            RichTextColor.DEFAULT, RichTextColor.WHITE -> Color(0xFFF1EFF4)
            RichTextColor.BLACK -> Color(0xFFC9C5D0)
            RichTextColor.RED -> Color(0xFFFFB4AB)
            RichTextColor.GREEN -> Color(0xFFA8DDA0)
            RichTextColor.YELLOW -> Color(0xFFFFDA6A)
            RichTextColor.BLUE -> Color(0xFFB6CBFF)
            RichTextColor.MAGENTA -> Color(0xFFFFB1E5)
            RichTextColor.CYAN -> Color(0xFF8BE7ED)
        }
    } else {
        when (this) {
            RichTextColor.DEFAULT, RichTextColor.BLACK -> Color(0xFF1D1B20)
            RichTextColor.WHITE -> Color(0xFF57525E)
            RichTextColor.RED -> Color(0xFFB3261E)
            RichTextColor.GREEN -> Color(0xFF24752A)
            RichTextColor.YELLOW -> Color(0xFF7A5900)
            RichTextColor.BLUE -> Color(0xFF2356A8)
            RichTextColor.MAGENTA -> Color(0xFF8D245C)
            RichTextColor.CYAN -> Color(0xFF006970)
        }
    }
    return if (emphasis == RichTextEmphasis.BRIGHT) {
        normal.copy(alpha = 1f)
    } else {
        normal.copy(alpha = if (isDark) 0.88f else 0.92f)
    }
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
    ReplyReference(
        targetId = targetId,
        preview = preview,
        compact = false,
        onClick = onReplyToClick?.let { callback -> { callback(targetId) } },
    )
}

@Composable
fun Paragraph.ReplyTo.Small(onReplyToClick: ((String) -> Unit)? = null) {
    ReplyReference(
        targetId = targetId,
        preview = preview,
        compact = true,
        onClick = onReplyToClick?.let { callback -> { callback(targetId) } },
    )
}

@Composable
private fun ReplyReference(
    targetId: String,
    preview: String?,
    compact: Boolean,
    onClick: (() -> Unit)?,
) {
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.appClickable(
            onClickLabel = "跳到被引用貼文",
            onClick = onClick,
        )
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(clickModifier),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(
                    horizontal = if (compact) 10.dp else 12.dp,
                    vertical = if (compact) 7.dp else 9.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (preview.isNullOrBlank()) 24.dp else 34.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "回覆 #${targetId.takeLast(10)}",
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
                preview?.takeIf(String::isNotBlank)?.let { previewText ->
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun Paragraph.Link.View() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val resourceProvider = remember(context) { context.hostResourceProvider() }
    val handle = remember(content) { ExternalLinkHandle.parse(content) }
    TextButton(
        onClick = {
            handle?.let { openExternalLink(it.asModel(), resourceProvider::consumeExternalLink, uriHandler::openUri) }
        },
        enabled = handle != null,
        contentPadding = PaddingValues(0.dp),
        shape = RectangleShape,
    ) { Text(if (handle == null) "連結已封鎖" else "在瀏覽器開啟連結") }
}

@Composable
fun Paragraph.Link.Small() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val resourceProvider = remember(context) { context.hostResourceProvider() }
    val handle = remember(content) { ExternalLinkHandle.parse(content) }
    TextButton(
        onClick = {
            handle?.let { openExternalLink(it.asModel(), resourceProvider::consumeExternalLink, uriHandler::openUri) }
        },
        enabled = handle != null,
        contentPadding = PaddingValues(0.dp),
        shape = RectangleShape,
    ) { BodySmallText(if (handle == null) "連結已封鎖" else "在瀏覽器開啟連結") }
}

@Composable
fun Paragraph.ImageInfo.View(
    alwaysUseRawImage: Boolean,
    onClick: (() -> Unit)? = null
) {
    val model = resourceModelOrNull(selectImageUrl(raw, thumb, alwaysUseRawImage))
    val imageModifier = if (onClick == null) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().appClickable(onClick = onClick)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Text(
                text = "遠端圖片已封鎖",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = model,
                modifier = imageModifier,
                contentDescription = null,
            )
        }
    }
}


@Composable
fun Paragraph.VideoInfo.View(onClick: (() -> Unit)? = null) {
    VideoPlayer(
        handleModel = url,
        modifier = Modifier.fillMaxWidth().height(220.dp),
    )
}

internal fun openExternalLink(
    handleModel: String,
    consume: (ExternalLinkHandle) -> String,
    openUri: (String) -> Unit,
): Boolean {
    val handle = ExternalLinkHandle.parse(handleModel) ?: return false
    return runCatching {
        openUri(consume(handle))
        true
    }.getOrDefault(false)
}
