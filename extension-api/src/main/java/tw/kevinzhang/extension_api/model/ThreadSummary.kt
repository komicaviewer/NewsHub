package tw.kevinzhang.extension_api.model

data class ThreadSummary(
    val sourceId: String,
    val boardUrl: String,
    val id: String,
    val title: String?,
    val author: String?,
    val createdAt: Long?,
    val replyCount: Int?,
    val thumbnail: String?,
    val previewContent: List<Paragraph>,
)
