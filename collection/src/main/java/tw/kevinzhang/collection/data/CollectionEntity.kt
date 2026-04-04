package tw.kevinzhang.collection.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,   // UUID
    val name: String,
    val sortOrder: Int,
    val description: String = "",
    val emoji: String = "📰",
)
