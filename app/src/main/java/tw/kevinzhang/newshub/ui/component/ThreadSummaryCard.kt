package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_api.model.plainText
import tw.kevinzhang.newshub.data.TimelineDisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSummaryCard(
    summary: ThreadSummary,
    alwaysUseRawImage: Boolean,
    sourceIconUrl: String?,
    sourceName: String? = null,
    boardName: String? = null,
    displayMode: TimelineDisplayMode = TimelineDisplayMode.MEDIA_FIRST,
    isRead: Boolean? = null,
    onClick: () -> Unit,
) {
    val content = summary.cardContent(alwaysUseRawImage)
    val presentation = summary.cardPresentation(content)
    val isUnread = isRead == false

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                isRead?.let { stateDescription = if (it) "已讀" else "未讀" }
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThreadMetadata(
                    summary = summary,
                    sourceIconUrl = sourceIconUrl,
                    sourceName = sourceName,
                    boardName = boardName,
                    isUnread = isUnread,
                )

                when (displayMode) {
                    TimelineDisplayMode.COMPACT -> CompactThreadContent(
                        presentation = presentation,
                        content = content,
                        isUnread = isUnread,
                    )

                    TimelineDisplayMode.MEDIA_FIRST -> MediaFirstThreadContent(
                        presentation = presentation,
                        content = content,
                        isUnread = isUnread,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadMetadata(
    summary: ThreadSummary,
    sourceIconUrl: String?,
    sourceName: String?,
    boardName: String?,
    isUnread: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sourceIconUrl != null) {
            AsyncImage(
                model = sourceIconUrl,
                contentDescription = sourceName?.let { "$it 圖示" },
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = summary.sourceBoardLabel(sourceName, boardName),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelMedium,
            color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        summary.createdAt?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = android.text.format.DateUtils.getRelativeTimeSpanString(it).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (isUnread) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        ThreadCounts(summary = summary)
    }
}

@Composable
private fun ThreadCounts(summary: ThreadSummary) {
    val replyCount = summary.replyCount
    val commentCount = summary.commentCount?.takeIf { it > 0 }
    if (replyCount == null && commentCount == null) return

    Spacer(Modifier.width(12.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        replyCount?.let {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        commentCount?.let {
            if (replyCount != null) Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactThreadContent(
    presentation: ThreadSummaryCardPresentation,
    content: ThreadSummaryCardContent,
    isUnread: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ThreadTitle(presentation.title, isUnread)
            presentation.preview?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "貼文附圖，點擊可查看完整內容",
                modifier = Modifier
                    .width(112.dp)
                    .height(84.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MediaFirstThreadContent(
    presentation: ThreadSummaryCardPresentation,
    content: ThreadSummaryCardContent,
    isUnread: Boolean,
) {
    content.imageUrl?.let { imageUrl ->
        AsyncImage(
            model = imageUrl,
            contentDescription = "貼文附圖，點擊可查看完整內容",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop,
        )
    }
    ThreadTitle(presentation.title, isUnread)
    presentation.preview?.let { preview ->
        Text(
            text = preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThreadTitle(title: String?, isUnread: Boolean) {
    title?.takeIf(String::isNotBlank)?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal data class ThreadSummaryCardContent(
    val previewContent: List<Paragraph>,
    val imageUrl: String?,
)

internal data class ThreadSummaryCardPresentation(
    val title: String?,
    val preview: String?,
)

internal fun ThreadSummary.cardContent(alwaysUseRawImage: Boolean): ThreadSummaryCardContent {
    val videoUrls = mutableSetOf<String>()
    val uniquePreviewContent = previewContent.filter { paragraph ->
        paragraph !is Paragraph.VideoInfo || videoUrls.add(paragraph.url)
    }
    val selectedImageUrl = if (alwaysUseRawImage) rawImage else thumbnail

    return ThreadSummaryCardContent(
        previewContent = uniquePreviewContent,
        imageUrl = selectedImageUrl?.takeUnless(videoUrls::contains),
    )
}

internal fun ThreadSummaryCardContent.compactPreviewText(): String? =
    previewContent.mapNotNull { paragraph ->
        val text = when (paragraph) {
            is Paragraph.Text -> paragraph.content
            is Paragraph.RichText -> paragraph.plainText()
            is Paragraph.Quote -> "> ${paragraph.content}"
            is Paragraph.Link -> paragraph.content
            is Paragraph.ReplyTo -> ">> ${paragraph.targetId}${paragraph.preview?.let { " ($it)" } ?: ""}"
            else -> null
        }
        text?.trim()?.takeIf(String::isNotBlank)
    }.joinToString(separator = "\n").takeIf(String::isNotBlank)

internal fun ThreadSummary.cardPresentation(
    content: ThreadSummaryCardContent,
): ThreadSummaryCardPresentation {
    val previewLines = content.compactPreviewText()
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter(String::isNotBlank)
        ?.toList()
        .orEmpty()
    val trimmedTitle = title?.trim()?.takeIf(String::isNotBlank)
    val hasPlaceholderTitle = trimmedTitle.equals("無題", ignoreCase = true) ||
        trimmedTitle.equals("untitled", ignoreCase = true)
    if (!hasPlaceholderTitle && trimmedTitle != null) {
        return ThreadSummaryCardPresentation(
            title = trimmedTitle,
            preview = previewLines.joinToString("\n").takeIf(String::isNotBlank),
        )
    }
    if (previewLines.isNotEmpty()) {
        return ThreadSummaryCardPresentation(
            title = previewLines.first(),
            preview = previewLines.drop(1).joinToString("\n").takeIf(String::isNotBlank),
        )
    }
    return ThreadSummaryCardPresentation(title = trimmedTitle, preview = null)
}

internal fun ThreadSummary.sourceBoardLabel(sourceName: String?, boardName: String?): String =
    listOfNotNull(sourceName?.takeIf(String::isNotBlank), boardName?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .ifBlank { boardUrl }
