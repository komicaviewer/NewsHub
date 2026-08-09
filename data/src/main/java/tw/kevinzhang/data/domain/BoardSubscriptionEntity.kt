package tw.kevinzhang.data.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity(
    tableName = "board_subscriptions",
    foreignKeys = [ForeignKey(
        entity = SourceIdentityEntity::class,
        parentColumns = ["sourceKey"],
        childColumns = ["sourceKey"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("sourceKey")],
)
data class BoardSubscriptionEntity(
    @PrimaryKey val id: String,   // UUID
    val collectionId: String,
    val sourceKey: String,
    val boardUrl: String,
    val boardName: String,        // cached — avoids loading Source just to show the name
    val sortOrder: Int,
)

data class BoardSubscriptionRecord(
    @Embedded val subscription: BoardSubscriptionEntity,
    @Relation(parentColumn = "sourceKey", entityColumn = "sourceKey")
    val sourceIdentity: SourceIdentityEntity,
)
