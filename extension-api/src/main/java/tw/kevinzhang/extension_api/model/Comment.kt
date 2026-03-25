package tw.kevinzhang.extension_api.model

data class Comment(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val content: List<Paragraph>,
)
