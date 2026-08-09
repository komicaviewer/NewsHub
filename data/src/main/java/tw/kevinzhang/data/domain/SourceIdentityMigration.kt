package tw.kevinzhang.data.domain

import androidx.sqlite.db.SupportSQLiteDatabase

internal fun migrateLegacySourcesToCanonicalIdentities(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE `source_identities` (
            `sourceKey` TEXT NOT NULL,
            `packageName` TEXT,
            `signerSha256` TEXT,
            `sourceId` TEXT NOT NULL,
            `resolution` TEXT NOT NULL,
            PRIMARY KEY(`sourceKey`)
        )
        """.trimIndent(),
    )
    database.execSQL("CREATE INDEX `index_source_identities_sourceId` ON `source_identities` (`sourceId`)")
    database.execSQL(
        "CREATE UNIQUE INDEX `index_source_identities_packageName_signerSha256_sourceId` " +
            "ON `source_identities` (`packageName`, `signerSha256`, `sourceId`)",
    )
    database.execSQL(
        "CREATE TEMP TABLE `legacy_source_map` (`sourceId` TEXT NOT NULL PRIMARY KEY, `sourceKey` TEXT NOT NULL)",
    )

    val sourceIds = mutableSetOf<String>()
    listOf("board_subscriptions", "reading_history", "saved_posts", "post_read_states").forEach { table ->
        database.query("SELECT DISTINCT `sourceId` FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) sourceIds += cursor.getString(0)
        }
    }
    sourceIds.sorted().forEach { sourceId ->
        val identity = CanonicalSourceIdentities.fromLegacySourceId(sourceId)
        database.execSQL(
            "INSERT INTO `source_identities` " +
                "(`sourceKey`, `packageName`, `signerSha256`, `sourceId`, `resolution`) VALUES (?, ?, ?, ?, ?)",
            arrayOf(
                identity.sourceKey,
                identity.packageName,
                identity.signerSha256,
                identity.sourceId,
                identity.resolution.name,
            ),
        )
        database.execSQL(
            "INSERT INTO `legacy_source_map` (`sourceId`, `sourceKey`) VALUES (?, ?)",
            arrayOf(sourceId, identity.sourceKey),
        )
    }

    migrateBoardSubscriptions(database)
    migrateReadingHistory(database)
    migrateSavedPosts(database)
    migratePostReadStates(database)
    database.execSQL("DROP TABLE `legacy_source_map`")
}

private fun migrateBoardSubscriptions(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE `board_subscriptions_new` (
            `id` TEXT NOT NULL,
            `collectionId` TEXT NOT NULL,
            `sourceKey` TEXT NOT NULL,
            `boardUrl` TEXT NOT NULL,
            `boardName` TEXT NOT NULL,
            `sortOrder` INTEGER NOT NULL,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`sourceKey`) REFERENCES `source_identities`(`sourceKey`)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO `board_subscriptions_new`
            (`id`, `collectionId`, `sourceKey`, `boardUrl`, `boardName`, `sortOrder`)
        SELECT old.`id`, old.`collectionId`, map.`sourceKey`, old.`boardUrl`, old.`boardName`, old.`sortOrder`
        FROM `board_subscriptions` old
        JOIN `legacy_source_map` map ON map.`sourceId` = old.`sourceId`
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE `board_subscriptions`")
    database.execSQL("ALTER TABLE `board_subscriptions_new` RENAME TO `board_subscriptions`")
    database.execSQL("CREATE INDEX `index_board_subscriptions_sourceKey` ON `board_subscriptions` (`sourceKey`)")
}

private fun migrateReadingHistory(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE `reading_history_new` (
            `sourceKey` TEXT NOT NULL, `sourceName` TEXT, `threadId` TEXT NOT NULL,
            `boardUrl` TEXT NOT NULL, `boardName` TEXT, `title` TEXT, `author` TEXT,
            `createdAt` INTEGER, `commentCount` INTEGER, `replyCount` INTEGER,
            `thumbnail` TEXT, `rawImage` TEXT, `previewContent` TEXT NOT NULL,
            `sourceIconUrl` TEXT, `readAt` INTEGER NOT NULL,
            PRIMARY KEY(`sourceKey`, `threadId`),
            FOREIGN KEY(`sourceKey`) REFERENCES `source_identities`(`sourceKey`)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO `reading_history_new`
        SELECT map.`sourceKey`, old.`sourceName`, old.`threadId`, old.`boardUrl`, old.`boardName`,
            old.`title`, old.`author`, old.`createdAt`, old.`commentCount`, old.`replyCount`,
            old.`thumbnail`, old.`rawImage`, old.`previewContent`, old.`sourceIconUrl`, old.`readAt`
        FROM `reading_history` old
        JOIN `legacy_source_map` map ON map.`sourceId` = old.`sourceId`
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE `reading_history`")
    database.execSQL("ALTER TABLE `reading_history_new` RENAME TO `reading_history`")
    database.execSQL("CREATE INDEX `index_reading_history_sourceKey` ON `reading_history` (`sourceKey`)")
}

private fun migrateSavedPosts(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE `saved_posts_new` (
            `sourceKey` TEXT NOT NULL, `sourceName` TEXT, `threadId` TEXT NOT NULL,
            `boardUrl` TEXT NOT NULL, `boardName` TEXT, `title` TEXT, `author` TEXT,
            `createdAt` INTEGER, `commentCount` INTEGER, `replyCount` INTEGER,
            `thumbnail` TEXT, `rawImage` TEXT, `previewContent` TEXT NOT NULL,
            `sourceIconUrl` TEXT, `threadUrl` TEXT, `savedAt` INTEGER NOT NULL,
            `screenshotAssetRefs` TEXT NOT NULL,
            PRIMARY KEY(`sourceKey`, `threadId`),
            FOREIGN KEY(`sourceKey`) REFERENCES `source_identities`(`sourceKey`)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO `saved_posts_new`
        SELECT map.`sourceKey`, old.`sourceName`, old.`threadId`, old.`boardUrl`, old.`boardName`,
            old.`title`, old.`author`, old.`createdAt`, old.`commentCount`, old.`replyCount`,
            old.`thumbnail`, old.`rawImage`, old.`previewContent`, old.`sourceIconUrl`,
            old.`threadUrl`, old.`savedAt`, old.`screenshotAssetRefs`
        FROM `saved_posts` old
        JOIN `legacy_source_map` map ON map.`sourceId` = old.`sourceId`
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE `saved_posts`")
    database.execSQL("ALTER TABLE `saved_posts_new` RENAME TO `saved_posts`")
    database.execSQL("CREATE INDEX `index_saved_posts_sourceKey` ON `saved_posts` (`sourceKey`)")
}

private fun migratePostReadStates(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE `post_read_states_new` (
            `sourceKey` TEXT NOT NULL, `threadId` TEXT NOT NULL, `postId` TEXT NOT NULL,
            `readAt` INTEGER NOT NULL,
            PRIMARY KEY(`sourceKey`, `threadId`, `postId`),
            FOREIGN KEY(`sourceKey`) REFERENCES `source_identities`(`sourceKey`)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        INSERT INTO `post_read_states_new`
        SELECT map.`sourceKey`, old.`threadId`, old.`postId`, old.`readAt`
        FROM `post_read_states` old
        JOIN `legacy_source_map` map ON map.`sourceId` = old.`sourceId`
        """.trimIndent(),
    )
    database.execSQL("DROP TABLE `post_read_states`")
    database.execSQL("ALTER TABLE `post_read_states_new` RENAME TO `post_read_states`")
    database.execSQL("CREATE INDEX `index_post_read_states_sourceKey` ON `post_read_states` (`sourceKey`)")
}
