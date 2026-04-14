package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary

@Composable
fun ThreadSummaryCard(
    summary: ThreadSummary,
    alwaysUseRawImage: Boolean,
    sourceIconUrl: String?,
    onClick: () -> Unit,
) {
    AppCard(onClick = onClick) {
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
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    BodySmallText(summary.author ?: "Unknown")
                    BodySmallText(summary.id.takeLast(10))
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
            summary.title?.let { title ->
                if (title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TitleMediumText(text = title)
                }
            }

            summary.previewContent.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> paragraph.View()
                    is Paragraph.Quote -> paragraph.Small()
                    is Paragraph.Link -> paragraph.View()
                    else -> {}
                }
            }

            val url = if (alwaysUseRawImage) summary.rawImage else summary.thumbnail
            url?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}
