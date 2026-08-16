package tw.kevinzhang.data.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import androidx.room.Relation
import tw.kevinzhang.extension_api.model.ThreadSummary

@Entity(
    tableName = "reading_history",
    primaryKeys = ["sourceKey", "threadId"],
    foreignKeys = [ForeignKey(
        entity = SourceIdentityEntity::class,
        parentColumns = ["sourceKey"],
        childColumns = ["sourceKey"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("sourceKey")],
)
data class ReadingHistoryEntity(
    val sourceKey: String,
    val sourceName: String? = null,
    val threadId: String,
    val boardUrl: String,
    val boardName: String? = null,
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
    fun toThreadSummary(sourceId: String): ThreadSummary = ThreadSummary(
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

data class ReadingHistoryRecord(
    @Embedded val history: ReadingHistoryEntity,
    @Relation(parentColumn = "sourceKey", entityColumn = "sourceKey")
    val sourceIdentity: SourceIdentityEntity,
) {
    fun toThreadSummary(): ThreadSummary = history.toThreadSummary(sourceIdentity.sourceId)
}
