package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import tw.kevinzhang.newshub.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import tw.kevinzhang.hub_server.data.*
import tw.kevinzhang.newshub.ui.theme.*

@Composable
fun ParagraphBlock(
    article: List<Paragraph>,
    textLengthMax: Int? = null,
    onParagraphClick: (Paragraph) -> Unit,
    onPreviewReplyTo: (Paragraph.ReplyTo) -> String,
) {
    ParagraphBlockWithPainter(
        article = article.map { ParagraphPainter(it, Modifier) },
        textLengthMax = textLengthMax,
        onParagraphClick = onParagraphClick,
        onPreviewReplyTo = onPreviewReplyTo,
    )
}

data class ParagraphPainter(
    val paragraph: Paragraph,
    val modifier: Modifier = Modifier,
    val fontColor: Color? = null,
)

@Composable
fun ParagraphBlockWithPainter(
    article: List<ParagraphPainter>,
    textLengthMax: Int? = null,
    onParagraphClick: (Paragraph) -> Unit,
    onPreviewReplyTo: (Paragraph.ReplyTo) -> String,
) {
    Column {
        article.map {
            when (it.paragraph) {
                is Paragraph.Text -> TextParagraph(it.paragraph, it.modifier, it.fontColor)
                is Paragraph.ImageInfo -> ImageParagraph(it.paragraph, it.modifier) { onParagraphClick(it.paragraph) }
                is Paragraph.VideoInfo -> VideoParagraph(it.paragraph, it.modifier) { onParagraphClick(it.paragraph) }
                is Paragraph.Link -> LinkParagraph(it.paragraph, it.modifier) { onParagraphClick(it.paragraph) }
                is Paragraph.ReplyTo -> ReplyToParagraph(it.paragraph, it.modifier, onPreviewReplyTo) { onParagraphClick(it.paragraph) }
                is Paragraph.Quote -> QuoteParagraph(it.paragraph, it.modifier)
            }
        }
    }
}

@Preview
@Composable
fun PreviewParagraphBlock() {
    NewshubTheme {
        ParagraphBlock(
            article = listOf(
                Paragraph.ReplyTo("Kevin"),
                Paragraph.Quote("Hi Google"),
                Paragraph.Text("Hi Kevin, welcome!"),
                Paragraph.ImageInfo(
                    "https://i.imgur.com/1Z1Z1Z1.jpg",
                    "https://i.imgur.com/1Z1Z1Z1.jpg"
                ),
                Paragraph.Text("see about us:"),
                Paragraph.Link("https://www.google.com")
            ),
            onParagraphClick = { },
            onPreviewReplyTo = { "" }
        )
    }
}

@Composable
fun TextParagraph(paragraph: Paragraph.Text, modifier: Modifier = Modifier, color: Color? = null) {
    if (color == null) {
        Text(
            modifier = modifier,
            text = paragraph.content,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(
            color = color,
            modifier = modifier,
            text = paragraph.content,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun ImageParagraph(paragraph: Paragraph.ImageInfo, modifier: Modifier = Modifier, onClick: () -> Unit = { }) {
    SubcomposeAsyncImage(
        model = paragraph.thumb,
        modifier = modifier
            .size(80.dp, 80.dp)
            .clickable(onClick = onClick),
        contentDescription = null,
        loading = {
            CircularProgressIndicator()
        },
    )
}

@Composable
fun LinkParagraph(paragraph: Paragraph.Link, modifier: Modifier = Modifier, color: Color = AppLink, onClick: () -> Unit = { }) {
    val annotated = buildAnnotatedString {
        append(paragraph.content)
        val (start, end) = 0 to paragraph.content.length
        addStyle(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, end)
        addStringAnnotation(
            tag = paragraph.content,
            annotation = paragraph.content,
            start = start,
            end = end
        )
    }
    ClickableText(
        modifier = modifier,
        text = annotated,
        onClick = { onClick() },
    )
}

@Composable
fun ReplyToParagraph(paragraph: Paragraph.ReplyTo, modifier: Modifier = Modifier, onPreviewReplyTo: (Paragraph.ReplyTo) -> String, onClick: () -> Unit = { }) {
    val preview = onPreviewReplyTo(paragraph)
    val annotations = if (preview.isBlank()) {
        ">>${paragraph.id}"
    } else {
        ">>${paragraph.id}($preview...)"
    }
    LinkParagraph(Paragraph.Link(annotations), modifier, AppReplyTo, onClick)
}

@Composable
fun QuoteParagraph(paragraph: Paragraph.Quote, modifier: Modifier = Modifier) {
    TextParagraph(Paragraph.Text(paragraph.content), modifier, AppQuote)
    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_4)))
}

@Composable
fun VideoParagraph(paragraph: Paragraph.VideoInfo, modifier: Modifier = Modifier, onClick: () -> Unit = { }) {
    SubcomposeAsyncImage(
        model = paragraph.url,
        modifier = modifier
            .size(80.dp, 80.dp)
            .clickable(onClick = onClick),
        contentDescription = null,
        loading = {
            CircularProgressIndicator()
        },
    )
}