package tw.kevinzhang.collection.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CollectionEntity::class, BoardSubscriptionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
}
