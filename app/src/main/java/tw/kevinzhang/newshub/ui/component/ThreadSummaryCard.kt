package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.data.TimelineDisplayMode

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
    val compact = displayMode == TimelineDisplayMode.COMPACT

    AppCard(
        onClick = onClick,
        modifier = Modifier.semantics {
            isRead?.let { stateDescription = if (it) "已讀" else "未讀" }
        },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    summary.createdAt?.let {
                        BodySmallText(
                            text = android.text.format.DateUtils.getRelativeTimeSpanString(it)
                                .toString(),
                        )
                    }
                    sourceIconUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = sourceName?.let { "$it 圖示" },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (compact) {
                        BodySmallText(
                            text = summary.sourceBoardLabel(sourceName, boardName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        BodySmallText(summary.author ?: "Unknown")
                        BodySmallText(summary.id.takeLast(10))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    summary.replyCount?.let {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BodySmallText("$it")
                    }
                    summary.commentCount?.takeIf { it > 0 }?.let {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BodySmallText("$it")
                    }
                }
            }
            if (compact && isRead == false) {
                BodySmallText(
                    text = "未讀",
                    color = MaterialTheme.colorScheme.primary,
                    paddingTop = 2.dp,
                )
            }
            summary.title?.let { title ->
                if (title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TitleMediumText(
                        text = title,
                        maxLines = if (compact) 2 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (compact) {
                content.compactPreviewText()?.let { preview ->
                    Spacer(modifier = Modifier.height(4.dp))
                    BodyMediumText(
                        text = preview,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                content.previewContent.forEach { paragraph ->
                    when (paragraph) {
                        is Paragraph.Text -> paragraph.View()
                        is Paragraph.Quote -> paragraph.Small()
                        is Paragraph.Link -> paragraph.View()
                        is Paragraph.VideoInfo -> paragraph.View()
                        else -> {}
                    }
                }
            }

            content.imageUrl?.let { imageUrl ->
                if (compact) {
                    CompactPreviewImage(imageUrl)
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "貼文附圖，點擊可查看完整內容",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun CompactPreviewImage(imageUrl: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Keeping both dimensions bounded gives wide tablets the same scan-friendly card density
        // as phones, while preserving a true 16:9 thumbnail.
        val previewWidth = minOf(maxWidth, 220.dp * (16f / 9f))
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "貼文附圖，點擊可查看完整內容",
                modifier = Modifier
                    .width(previewWidth)
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal data class ThreadSummaryCardContent(
    val previewContent: List<Paragraph>,
    val imageUrl: String?,
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
            is Paragraph.Quote -> "> ${paragraph.content}"
            is Paragraph.Link -> paragraph.content
            is Paragraph.ReplyTo -> ">> ${paragraph.targetId}${paragraph.preview?.let { " ($it)" } ?: ""}"
            else -> null
        }
        text?.trim()?.takeIf(String::isNotBlank)
    }.joinToString(separator = "\n").takeIf(String::isNotBlank)

internal fun ThreadSummary.sourceBoardLabel(sourceName: String?, boardName: String?): String =
    listOfNotNull(sourceName?.takeIf(String::isNotBlank), boardName?.takeIf(String::isNotBlank))
        .joinToString(" · ")
        .ifBlank { boardUrl }
