package tw.kevinzhang.collection.data

import androidx.room.Entity
import tw.kevinzhang.extension_api.model.ThreadSummary

@Entity(
    tableName = "reading_history",
    primaryKeys = ["sourceId", "threadId"],
)
data class ReadingHistoryEntity(
    val sourceId: String,
    val threadId: String,
    val boardUrl: String,
    val title: String?,
    val author: String?,
    val createdAt: Long?,
    val commentCount: Int?,
    val replyCount: Int?,
    val thumbnail: String?,
    val rawImage: String?,
    val previewContent: String, // JSON-serialized List<Paragraph>
    val sourceIconUrl: String?,
    val readAt: Long,
) {
    fun toThreadSummary(): ThreadSummary = ThreadSummary(
        sourceId = sourceId,
        boardUrl = boardUrl,
        id = threadId,
        title = title,
        author = author,
        createdAt = createdAt,
        commentCount = commentCount,
        replyCount = replyCount,
        thumbnail = thumbnail,
        rawImage = rawImage,
        previewContent = ParagraphListConverter().fromJson(previewContent),
        sourceIconUrl = sourceIconUrl,
    )
}
