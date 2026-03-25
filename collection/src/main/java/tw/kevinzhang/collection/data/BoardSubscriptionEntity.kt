package tw.kevinzhang.collection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "board_subscriptions")
data class BoardSubscriptionEntity(
    @PrimaryKey val id: String,   // UUID
    val collectionId: String,
    val sourceId: String,
    val boardUrl: String,
    val boardName: String,        // cached — avoids loading Source just to show the name
    val sortOrder: Int,
)
