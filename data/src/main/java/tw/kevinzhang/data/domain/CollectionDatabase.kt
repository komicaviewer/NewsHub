package tw.kevinzhang.data.domain

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CollectionEntity::class,
        BoardSubscriptionEntity::class,
        ReadingHistoryEntity::class,
        SavedPostEntity::class,
        PostReadEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(ParagraphListConverter::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun postReadDao(): PostReadDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `post_read_states` (
                        `sourceId` TEXT NOT NULL,
                        `threadId` TEXT NOT NULL,
                        `postId` TEXT NOT NULL,
                        `readAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `threadId`, `postId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `reading_history` ADD COLUMN `sourceName` TEXT")
                database.execSQL("ALTER TABLE `reading_history` ADD COLUMN `boardName` TEXT")
                database.execSQL("ALTER TABLE `saved_posts` ADD COLUMN `sourceName` TEXT")
                database.execSQL("ALTER TABLE `saved_posts` ADD COLUMN `boardName` TEXT")
            }
        }
    }
}
