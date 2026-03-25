package tw.kevinzhang.newshub.ui.comment

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import tw.kevinzhang.newshub.R
import androidx.compose.ui.tooling.preview.Preview
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.comment.gamer.GamerComment
import tw.kevinzhang.hub_server.data.comment.gamer.mockGamerComment
import tw.kevinzhang.newshub.toHumanTime
import tw.kevinzhang.newshub.ui.component.ParagraphBlockWithPainter
import tw.kevinzhang.newshub.ui.component.ParagraphPainter
import tw.kevinzhang.newshub.ui.news.CardHeadTextBlock
import tw.kevinzhang.newshub.ui.theme.AppComment
import tw.kevinzhang.newshub.ui.theme.NewshubTheme


@Composable
fun GamerCommentCell(
    comment: GamerComment,
    onParagraphClick: (Paragraph) -> Unit = {},
) {
    Row(
        modifier = Modifier.padding(dimensionResource(id = R.dimen.space_8)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.space_8)),
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
        )
        Column {
            GamerCommentHeader(comment.posterName, comment.createdAt)
            ParagraphBlockWithPainter(
                article = comment.content.map { ParagraphPainter(paragraph = it, fontColor = AppComment) },
                onParagraphClick = onParagraphClick,
                onPreviewReplyTo = { "" }
            )
        }
    }
}

@Composable
private fun GamerCommentHeader(poster: String, time: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CardHeadTextBlock(text = poster, color = LocalContentColor.current)
        CardHeadTextBlock(text = time?.toHumanTime() ?: "null", )
    }
}

@Preview
@Composable
fun PreviewGamerCommentCell() {
    NewshubTheme {
        GamerCommentCell(mockGamerComment())
    }
}