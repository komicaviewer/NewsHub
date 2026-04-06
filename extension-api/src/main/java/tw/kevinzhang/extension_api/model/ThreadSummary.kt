package tw.kevinzhang.extension_api.model

data class ThreadSummary(
    val sourceId: String,
    val boardUrl: String,
    val id: String,
    val title: String?,
    val author: String?,
    val createdAt: Long?,
    val commentCount: Int?,
    val rawImage: String?,
    val thumbnail: String?,
    val previewContent: List<Paragraph>,
    val sourceIconUrl: String? = null,
    val replyCount: Int? = null,
)
