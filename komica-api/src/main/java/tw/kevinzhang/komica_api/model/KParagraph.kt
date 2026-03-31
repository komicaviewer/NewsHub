package tw.kevinzhang.komica_api.model

interface KParagraph

data class KVideoInfo(
    val url: String,
): KParagraph

data class KImageInfo(
    val thumb: String? = null,
    val raw: String,
): KParagraph

data class KText(
    val content: String,
): KParagraph

data class KQuote(
    val content: String,
): KParagraph

data class KReplyTo(
    val content: String,
): KParagraph

data class KLink(
    val content: String,
): KParagraph