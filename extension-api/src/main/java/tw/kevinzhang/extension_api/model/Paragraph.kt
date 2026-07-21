package tw.kevinzhang.extension_api.model

sealed class Paragraph {
    data class ImageInfo(val thumb: String? = null, val raw: String) : Paragraph()
    data class VideoInfo(val url: String, val site: Site = Site.OTHER) : Paragraph() {
        enum class Site { YOUTUBE, OTHER }
    }
    data class Text(val content: String) : Paragraph()
    data class Quote(val content: String) : Paragraph()
    data class ReplyTo(val targetId: String, val preview: String? = null) : Paragraph()
    data class Link(val content: String) : Paragraph()
    /**
     * Inline styled text supplied by an extension.
     *
     * Hosts should preserve the run order and line breaks. [RichTextLayout.PREFORMATTED_WRAP]
     * additionally asks the host to use a monospaced face while still wrapping on narrow screens.
     */
    data class RichText(
        val runs: List<RichTextRun>,
        val layout: RichTextLayout = RichTextLayout.PROSE,
    ) : Paragraph()
}

data class RichTextRun(
    val text: String,
    val color: RichTextColor = RichTextColor.DEFAULT,
    val emphasis: RichTextEmphasis = RichTextEmphasis.NORMAL,
    val linkUrl: String? = null,
)

/** Semantic palette rather than raw RGB values so each host theme can keep text legible. */
enum class RichTextColor {
    DEFAULT,
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
}

enum class RichTextEmphasis { NORMAL, BRIGHT }

enum class RichTextLayout {
    PROSE,
    PREFORMATTED_WRAP,
}

fun Paragraph.RichText.plainText(): String = runs.joinToString(separator = "") { it.text }

fun List<Paragraph>.rawImages() =
    filterIsInstance<Paragraph.ImageInfo>().map { it.raw }
