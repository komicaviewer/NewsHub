package tw.kevinzhang.hub_server.data

import tw.kevinzhang.gamer_api.model.*
import tw.kevinzhang.komica_api.model.*
import java.lang.IllegalArgumentException

sealed class Paragraph(
    val type: ParagraphType,
) {
    class ImageInfo(
        val thumb: String? = null,
        val raw: String,
    ): Paragraph(ParagraphType.IMAGE)

    class VideoInfo(
        val url: String,
    ): Paragraph(ParagraphType.VIDEO)

    class Text(
        val content: String,
    ): Paragraph(ParagraphType.TEXT)

    class Quote(
        val content: String,
    ): Paragraph(ParagraphType.QUOTE)

    class ReplyTo(
        val id: String,
    ): Paragraph(ParagraphType.REPLY_TO)

    class Link(
        val content: String,
    ): Paragraph(ParagraphType.LINK)
}

enum class ParagraphType {
    QUOTE, REPLY_TO, TEXT, IMAGE, LINK, VIDEO
}

fun List<Paragraph>.rawImages() =
    filterIsInstance<Paragraph.ImageInfo>().map { it.raw }

fun KParagraph.toParagraph(): Paragraph {
    return when (this) {
        is KQuote -> Paragraph.Quote(content)
        is KReplyTo -> Paragraph.ReplyTo(content)
        is KText -> Paragraph.Text(content)
        is KImageInfo -> Paragraph.ImageInfo(thumb, raw)
        is KVideoInfo -> Paragraph.VideoInfo(url)
        is KLink -> Paragraph.Link(content)
        else -> {
            throw IllegalArgumentException("Unknown paragraph type")
        }
    }
}

fun GParagraph.toParagraph(): Paragraph {
    return when (this) {
        is GQuote -> Paragraph.Quote(content)
        is GReplyTo -> Paragraph.ReplyTo(content)
        is GText -> Paragraph.Text(content)
        is GImageInfo -> Paragraph.ImageInfo(thumb, raw)
        is GLink -> Paragraph.Link(content)
        else -> {
            throw IllegalArgumentException("Unknown paragraph type")
        }
    }
}