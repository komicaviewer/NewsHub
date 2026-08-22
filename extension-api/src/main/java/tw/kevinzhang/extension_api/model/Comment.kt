package tw.kevinzhang.extension_api.model

data class Comment(
    val id: String,
    val author: String?,
    val createdAt: Long?,
    val content: List<Paragraph>,
    /** Null for a root comment; otherwise the stable id of its direct parent. */
    val parentId: String? = null,
)
