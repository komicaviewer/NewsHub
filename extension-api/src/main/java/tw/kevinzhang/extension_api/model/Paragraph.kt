package tw.kevinzhang.extension_api.model

sealed class Paragraph {
    class ImageInfo(val thumb: String? = null, val raw: String) : Paragraph()
    class VideoInfo(val url: String) : Paragraph()
    class Text(val content: String) : Paragraph()
    class Quote(val content: String) : Paragraph()
    class ReplyTo(val id: String) : Paragraph()
    class Link(val content: String) : Paragraph()
}

fun List<Paragraph>.rawImages() =
    filterIsInstance<Paragraph.ImageInfo>().map { it.raw }
