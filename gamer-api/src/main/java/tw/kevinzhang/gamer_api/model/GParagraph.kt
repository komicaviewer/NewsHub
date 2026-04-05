package tw.kevinzhang.gamer_api.model

interface GParagraph {
    val content: String
}

data class GImageInfo(
    val thumb: String? = null,
    val raw: String,
): GParagraph {
    override val content = thumb ?: raw
}

data class GText(
    override val content: String,
): GParagraph

data class GQuote(
    override val content: String,
): GParagraph

data class GReplyTo(
    override val content: String,
): GParagraph

data class GLink(
    override val content: String,
): GParagraph

fun List<GParagraph>.trim(): List<GParagraph> {
    val first = this.indexOfFirst { it.content.trim().isNotBlank() }
    val last = this.indexOfLast { it.content.trim().isNotBlank() }
    return if (first == -1 || last == -1)
        emptyList()
    else
        this.subList(first, last + 1)
}