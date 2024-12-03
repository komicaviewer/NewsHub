package tw.kevinzhang.newshub.model

import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.ParagraphType

data class Thumbnail(
    val url: String,
    val raw: String,
    val mediaType: ParagraphType
)

fun Paragraph.ImageInfo.toThumbnail(): Thumbnail {
    return Thumbnail(
        url = thumb ?: raw,
        raw = raw,
        mediaType = ParagraphType.IMAGE
    )
}

fun Paragraph.VideoInfo.toThumbnail(): Thumbnail {
    return Thumbnail(
        url = url,
        raw = url,
        mediaType = ParagraphType.VIDEO
    )
}