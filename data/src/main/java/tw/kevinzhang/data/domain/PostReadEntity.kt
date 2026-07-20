package tw.kevinzhang.data.domain

import androidx.room.Entity

/** Records that a post in a thread has entered the user's read viewport. */
@Entity(
    tableName = "post_read_states",
    primaryKeys = ["sourceId", "threadId", "postId"],
)
data class PostReadEntity(
    val sourceId: String,
    val threadId: String,
    val postId: String,
    val readAt: Long,
)
