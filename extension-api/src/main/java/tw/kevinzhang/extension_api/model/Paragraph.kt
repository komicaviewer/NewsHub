package tw.kevinzhang.extension_api.model

sealed class Paragraph {
    data class ImageInfo(val thumb: String? = null, val raw: String) : Paragraph()
    data class VideoInfo(val url: String) : Paragraph()
    data class Text(val content: String) : Paragraph()
    data class Quote(val content: String) : Paragraph()
    data class ReplyTo(val targetId: String, val preview: String? = null) : Paragraph()
    data class Link(val content: String) : Paragraph()
}

fun List<Paragraph>.rawImages() =
    filterIsInstance<Paragraph.ImageInfo>().map { it.raw }
