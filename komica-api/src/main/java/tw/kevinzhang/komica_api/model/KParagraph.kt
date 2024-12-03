package tw.kevinzhang.komica_api.model

interface KParagraph

class KVideoInfo(
    val url: String,
): KParagraph

class KImageInfo(
    val thumb: String? = null,
    val raw: String,
): KParagraph

class KText(
    val content: String,
): KParagraph

class KQuote(
    val content: String,
): KParagraph

class KReplyTo(
    val content: String,
): KParagraph

class KLink(
    val content: String,
): KParagraph