package tw.kevinzhang.data.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Records that a post in a thread has entered the user's read viewport. */
@Entity(
    tableName = "post_read_states",
    primaryKeys = ["sourceKey", "threadId", "postId"],
    foreignKeys = [ForeignKey(
        entity = SourceIdentityEntity::class,
        parentColumns = ["sourceKey"],
        childColumns = ["sourceKey"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("sourceKey")],
)
data class PostReadEntity(
    val sourceKey: String,
    val threadId: String,
    val postId: String,
    val readAt: Long,
)
