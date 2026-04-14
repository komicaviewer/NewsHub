package tw.kevinzhang.collection.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CollectionEntity::class, BoardSubscriptionEntity::class, ReadingHistoryEntity::class, SavedPostEntity::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(ParagraphListConverter::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun savedPostDao(): SavedPostDao
}
