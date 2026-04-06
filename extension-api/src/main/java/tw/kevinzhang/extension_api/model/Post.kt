package tw.kevinzhang.extension_api.model

data class Post(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val thumbnail: String?,
    val content: List<Paragraph>,
    val comments: List<Comment>,
    val rawHtml: String? = null,
    val sourceIconUrl: String? = null,
    val replyCount: Int? = null,
)
