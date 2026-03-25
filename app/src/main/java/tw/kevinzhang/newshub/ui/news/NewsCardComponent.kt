package tw.kevinzhang.newshub.ui.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import tw.kevinzhang.newshub.R
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.newshub.*
import tw.kevinzhang.newshub.ui.component.LinkParagraph
import tw.kevinzhang.newshub.ui.component.TextParagraph
import tw.kevinzhang.newshub.ui.theme.AppDisabledAlpha
import tw.kevinzhang.newshub.ui.theme.AppLink

@Composable
fun CardHeadTimeBlock(timestamp: Long?) {
    CardHeadTextBlock(timestamp?.toHumanTime() ?: "time")
}

@Composable
fun CardHeadRepliesBlock(replies: Int?, onShowMoreClick: (() -> Unit)? = null, showZero: Boolean = true) {
    if ((replies.isZeroOrNull() && showZero) || replies.isZeroOrNull().not()) {
        if (onShowMoreClick == null || replies.isZeroOrNull()) {
            CardHeadTextBlock(
                text = (replies ?: 0).toString(),
            )
        } else {
            CardHeadTextBlock(
                text = (replies ?: 0).toString(),
                color = AppLink,
                onClick = onShowMoreClick,
            )
        }
    }
}

@Composable
fun CardHeadBlock(
    modifier: Modifier = Modifier,
    compose: @Composable () -> Unit = { },
) {
    Box(
        modifier = modifier
            .padding(end = dimensionResource(id = R.dimen.space_4))
    ) {
        compose()
    }
}

@Composable
fun CardHeadTextBlock(
    text: String? = "",
    color: Color = LocalContentColor.current.copy(alpha = AppDisabledAlpha),
    onClick: (() -> Unit)? = null,
) {
    CardHeadBlock(
        modifier = Modifier
            .thenIfNotNull(onClick) { clickable(onClick = it) }
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
fun OriginalLinkParagraph(link: String, onClick: (Paragraph.Link) -> Unit = { }) {
    val original = Paragraph.Link(link)
    TextParagraph(paragraph = Paragraph.Text("原文連結："))
    LinkParagraph(
        paragraph = original,
        onClick = { onClick(original) },
    )
}