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
        SourceIdentityEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(ParagraphListConverter::class, SourceResolutionConverter::class)
abstract class CollectionDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun savedPostDao(): SavedPostDao
    abstract fun postReadDao(): PostReadDao
    abstract fun sourceIdentityDao(): SourceIdentityDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `collections` ADD COLUMN `description` TEXT NOT NULL DEFAULT ''",
                )
                database.execSQL(
                    "ALTER TABLE `collections` ADD COLUMN `emoji` TEXT NOT NULL DEFAULT '📰'",
                )
            }
        }

        /** Version 3 was never released; version 4 followed version 2 directly. */
        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Canonicalise defaults: version 1 requires temporary SQL defaults while adding
                // NOT NULL columns, whereas a clean version 2 database did not declare them.
                database.execSQL(
                    """
                    CREATE TABLE `collections_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO `collections_new` (`id`, `name`, `sortOrder`, `description`, `emoji`)
                    SELECT `id`, `name`, `sortOrder`, `description`, `emoji` FROM `collections`
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE `collections`")
                database.execSQL("ALTER TABLE `collections_new` RENAME TO `collections`")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_history` (
                        `sourceId` TEXT NOT NULL,
                        `threadId` TEXT NOT NULL,
                        `boardUrl` TEXT NOT NULL,
                        `title` TEXT,
                        `author` TEXT,
                        `createdAt` INTEGER,
                        `commentCount` INTEGER,
                        `replyCount` INTEGER,
                        `thumbnail` TEXT,
                        `rawImage` TEXT,
                        `previewContent` TEXT NOT NULL,
                        `sourceIconUrl` TEXT,
                        `readAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sourceId`, `threadId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_posts` (
                        `sourceId` TEXT NOT NULL,
                        `threadId` TEXT NOT NULL,
                        `boardUrl` TEXT NOT NULL,
                        `title` TEXT,
                        `author` TEXT,
                        `createdAt` INTEGER,
                        `commentCount` INTEGER,
                        `replyCount` INTEGER,
                        `thumbnail` TEXT,
                        `rawImage` TEXT,
                        `previewContent` TEXT NOT NULL,
                        `sourceIconUrl` TEXT,
                        `threadUrl` TEXT,
                        `savedAt` INTEGER NOT NULL,
                        `screenshotPaths` TEXT NOT NULL,
                        PRIMARY KEY(`sourceId`, `threadId`)
                    )
                    """.trimIndent(),
                )
            }
        }

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

        /**
         * Removes legacy absolute paths from persisted state. Metadata is retained, but old
         * screenshot cache entries intentionally cannot be interpreted or deleted from DB input.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_posts_new` (
                        `sourceId` TEXT NOT NULL,
                        `sourceName` TEXT,
                        `threadId` TEXT NOT NULL,
                        `boardUrl` TEXT NOT NULL,
                        `boardName` TEXT,
                        `title` TEXT,
                        `author` TEXT,
                        `createdAt` INTEGER,
                        `commentCount` INTEGER,
                        `replyCount` INTEGER,
                        `thumbnail` TEXT,
                        `rawImage` TEXT,
                        `previewContent` TEXT NOT NULL,
                        `sourceIconUrl` TEXT,
                        `threadUrl` TEXT,
                        `savedAt` INTEGER NOT NULL,
                        `screenshotAssetRefs` TEXT NOT NULL,
                        PRIMARY KEY(`sourceId`, `threadId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO `saved_posts_new` (
                        `sourceId`, `sourceName`, `threadId`, `boardUrl`, `boardName`, `title`,
                        `author`, `createdAt`, `commentCount`, `replyCount`, `thumbnail`, `rawImage`,
                        `previewContent`, `sourceIconUrl`, `threadUrl`, `savedAt`, `screenshotAssetRefs`
                    )
                    SELECT
                        `sourceId`, `sourceName`, `threadId`, `boardUrl`, `boardName`, `title`,
                        `author`, `createdAt`, `commentCount`, `replyCount`, `thumbnail`, `rawImage`,
                        `previewContent`, `sourceIconUrl`, `threadUrl`, `savedAt`, '[]'
                    FROM `saved_posts`
                    """.trimIndent(),
                )
                database.execSQL("DROP TABLE `saved_posts`")
                database.execSQL("ALTER TABLE `saved_posts_new` RENAME TO `saved_posts`")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateLegacySourcesToCanonicalIdentities(database)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateCanonicalIdentitiesToRepositoryDomains(database)
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
        )
    }
}
