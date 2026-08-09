package tw.kevinzhang.data.domain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import tw.kevinzhang.data.CollectionRepositoryImpl
import tw.kevinzhang.data.SavedPostAssetStore
import tw.kevinzhang.extension_api.SourceIdentity

@RunWith(AndroidJUnit4::class)
class CollectionDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: CollectionDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun version1MigratesTo8WithoutLosingCollections() {
        createLegacyDatabase(1) { db ->
            createVersion1Tables(db)
            db.execSQL("INSERT INTO collections VALUES ('collection', 'News', 3)")
            db.execSQL(
                "INSERT INTO board_subscriptions VALUES " +
                    "('subscription', 'collection', 'source', 'https://board', 'Board', 2)",
            )
        }

        val migrated = openCurrentDatabase()
        migrated.openHelper.readableDatabase.query(
            "SELECT name, sortOrder, description, emoji FROM collections WHERE id = ?",
            arrayOf("collection"),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("News", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
            assertEquals("📰", cursor.getString(3))
        }
        migrated.openHelper.readableDatabase.query(
            "SELECT resolution FROM source_identities WHERE sourceId = 'source'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("UNRESOLVED", cursor.getString(0))
        }
    }

    @Test
    fun version4MigratesTo8WithoutInterpretingAbsoluteScreenshotPaths() {
        createLegacyDatabase(4) { db ->
            createVersion1Tables(db)
            addVersion2Columns(db)
            createVersion4Tables(db)
            db.execSQL(
                """
                INSERT INTO saved_posts (
                    sourceId, threadId, boardUrl, title, author, createdAt, commentCount,
                    replyCount, thumbnail, rawImage, previewContent, sourceIconUrl,
                    threadUrl, savedAt, screenshotPaths
                ) VALUES (
                    'source', 'thread', 'https://board', 'Title', 'Author', 1, 2,
                    3, NULL, NULL, '[]', NULL, 'https://thread', 4, '[\"/tmp/sentinel\"]'
                )
                """.trimIndent(),
            )
        }

        val migrated = openCurrentDatabase()
        migrated.openHelper.readableDatabase.query(
            "SELECT post.title, post.sourceName, post.boardName, post.screenshotAssetRefs " +
                "FROM saved_posts post JOIN source_identities identity " +
                "ON identity.sourceKey = post.sourceKey " +
                "WHERE identity.sourceId = ? AND post.threadId = ?",
            arrayOf("source", "thread"),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("Title", cursor.getString(0))
            assertEquals(null, cursor.getString(1))
            assertEquals(null, cursor.getString(2))
            assertEquals("[]", cursor.getString(3))
        }
    }

    @Test
    fun versions2_5And6AllReachVersion8WithoutDestructiveFallback() {
        listOf(2, 5, 6).forEach { version ->
            database?.close()
            database = null
            createLegacyDatabase(version) { db ->
                createVersion1Tables(db)
                addVersion2Columns(db)
                db.execSQL("INSERT INTO collections VALUES ('collection', 'v$version', 1, '', '📰')")
                if (version >= 4) createVersion4Tables(db)
                if (version >= 5) createVersion5Table(db)
                if (version >= 6) addVersion6Columns(db)
            }

            val migrated = openCurrentDatabase()
            migrated.openHelper.readableDatabase.query("PRAGMA user_version").use { cursor ->
                cursor.moveToFirst()
                assertEquals(8, cursor.getInt(0))
            }
            migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM collections").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version7MigratesOfficialAndUnknownSourcesWithoutLosingRowsOrAssets() {
        val official = "tw.kevinzhang.newshub.extension.hackernews"
        createLegacyDatabase(7) { db ->
            createVersion1Tables(db)
            addVersion2Columns(db)
            createVersion4Tables(db)
            createVersion5Table(db)
            addVersion6Columns(db)
            migrateSavedPostsToVersion7(db)
            db.execSQL("INSERT INTO collections VALUES ('collection', 'News', 0, '', '📰')")
            listOf(official, "unknown.source").forEachIndexed { index, sourceId ->
                db.execSQL(
                    "INSERT INTO board_subscriptions VALUES (?, 'collection', ?, ?, ?, ?)",
                    arrayOf<Any>("subscription-$index", sourceId, "board-$index", "Board $index", index),
                )
                db.execSQL(
                    "INSERT INTO reading_history " +
                        "(sourceId, sourceName, threadId, boardUrl, boardName, title, previewContent, readAt) " +
                        "VALUES (?, NULL, ?, ?, NULL, ?, '[]', ?)",
                    arrayOf<Any>(sourceId, "thread-$index", "board-$index", "Title $index", index + 1),
                )
                db.execSQL(
                    "INSERT INTO saved_posts " +
                        "(sourceId, sourceName, threadId, boardUrl, boardName, title, previewContent, savedAt, screenshotAssetRefs) " +
                        "VALUES (?, NULL, ?, ?, NULL, ?, '[]', ?, ?)",
                    arrayOf<Any>(
                        sourceId,
                        "thread-$index",
                        "board-$index",
                        "Title $index",
                        index + 1,
                        "[\"${if (index == 0) "a".repeat(64) else "b".repeat(64)}/post_0.png\"]",
                    ),
                )
                db.execSQL(
                    "INSERT INTO post_read_states VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(sourceId, "thread-$index", "post-$index", index + 1),
                )
            }
        }

        val migrated = openCurrentDatabase().openHelper.readableDatabase
        listOf("board_subscriptions", "reading_history", "saved_posts", "post_read_states").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                cursor.moveToFirst()
                assertEquals(2, cursor.getInt(0))
            }
        }
        migrated.query(
            "SELECT resolution, packageName FROM source_identities WHERE sourceId = ?",
            arrayOf(official),
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("OFFICIAL", cursor.getString(0))
            assertEquals("tw.kevinzhang.newshub.extension.hackernews", cursor.getString(1))
        }
        migrated.query(
            "SELECT resolution, packageName FROM source_identities WHERE sourceId = 'unknown.source'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("UNRESOLVED", cursor.getString(0))
            assertEquals(null, cursor.getString(1))
        }
        migrated.query("SELECT screenshotAssetRefs FROM saved_posts ORDER BY threadId").use { cursor ->
            cursor.moveToFirst()
            assertEquals("[\"${"a".repeat(64)}/post_0.png\"]", cursor.getString(0))
            cursor.moveToNext()
            assertEquals("[\"${"b".repeat(64)}/post_0.png\"]", cursor.getString(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
    }

    @Test
    fun version7MigrationRollsBackAllSchemaAndDataChangesAtomically() {
        createLegacyDatabase(7) { db ->
            createVersion1Tables(db)
            addVersion2Columns(db)
            createVersion4Tables(db)
            createVersion5Table(db)
            addVersion6Columns(db)
            migrateSavedPostsToVersion7(db)
            db.execSQL("INSERT INTO collections VALUES ('collection', 'News', 0, '', '📰')")
            db.execSQL(
                "INSERT INTO board_subscriptions VALUES " +
                    "('subscription', 'collection', 'unknown.source', 'board', 'Board', 0)",
            )
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(7) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        val support = helper.writableDatabase
        try {
            support.beginTransaction()
            try {
                CollectionDatabase.MIGRATION_7_8.migrate(support)
                // Deliberately omit setTransactionSuccessful(): Room migrations use the same
                // transaction boundary and must leave no partial v8 schema after a failure.
            } finally {
                support.endTransaction()
            }

            support.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'source_identities'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            support.query("SELECT sourceId FROM board_subscriptions WHERE id = 'subscription'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("unknown.source", cursor.getString(0))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun migratedUnresolvedIdentityIsOfflineOnlyButVerifiedRuntimeIdentityIsOnline() = runBlocking {
        val current = openCurrentDatabase()
        val repository = CollectionRepositoryImpl(
            dao = current.collectionDao(),
            db = current,
            savedPostAssetStore = SavedPostAssetStore.forAppFilesDirectory(context.cacheDir),
            sourceIdentityDao = current.sourceIdentityDao(),
        )
        val collectionId = repository.createCollection("Test", "", "📰")
        val unresolved = CanonicalSourceIdentities.fromLegacySourceId("unknown.source")
        current.sourceIdentityDao().insert(unresolved)
        assertFalse(unresolved.canAccessNetworkOrCredentials)
        val rejected = runCatching {
            repository.addBoardSubscription(collectionId, unresolved.sourceKey, "board", "Board")
        }
        assertTrue(rejected.isFailure)

        val official = repository.register(
            SourceIdentity(
                "tw.kevinzhang.newshub.extension.hackernews",
                // API29 emulator fixture. Production pins remain owned and verified by the Host.
                "99c1fb14a8a91ca27d2514834bd95c0aad013bb20502dafe7bc3034910a521d1",
                "tw.kevinzhang.newshub.extension.hackernews",
            ),
        )
        assertTrue(official.canAccessNetworkOrCredentials)
        repository.addBoardSubscription(collectionId, official.sourceKey, "board", "Board")
        assertEquals(1, current.collectionDao().countSubscription(collectionId, official.sourceKey, "board"))
    }

    private fun openCurrentDatabase(): CollectionDatabase =
        Room.databaseBuilder(context, CollectionDatabase::class.java, TEST_DB)
            .addMigrations(*CollectionDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also {
                database = it
                it.openHelper.writableDatabase
            }

    private fun createLegacyDatabase(version: Int, create: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(TEST_DB)
        object : SQLiteOpenHelper(context, TEST_DB, null, version) {
            override fun onCreate(db: SQLiteDatabase) = create(db)
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }.writableDatabase.close()
    }

    private fun createVersion1Tables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE collections " +
                "(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)",
        )
        db.execSQL(
            """
            CREATE TABLE board_subscriptions (
                id TEXT NOT NULL PRIMARY KEY,
                collectionId TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                boardUrl TEXT NOT NULL,
                boardName TEXT NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun addVersion2Columns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE collections ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE collections ADD COLUMN emoji TEXT NOT NULL DEFAULT '📰'")
    }

    private fun createVersion4Tables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reading_history (
                sourceId TEXT NOT NULL, threadId TEXT NOT NULL, boardUrl TEXT NOT NULL,
                title TEXT, author TEXT, createdAt INTEGER, commentCount INTEGER,
                replyCount INTEGER, thumbnail TEXT, rawImage TEXT, previewContent TEXT NOT NULL,
                sourceIconUrl TEXT, readAt INTEGER NOT NULL,
                PRIMARY KEY(sourceId, threadId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE saved_posts (
                sourceId TEXT NOT NULL, threadId TEXT NOT NULL, boardUrl TEXT NOT NULL,
                title TEXT, author TEXT, createdAt INTEGER, commentCount INTEGER,
                replyCount INTEGER, thumbnail TEXT, rawImage TEXT, previewContent TEXT NOT NULL,
                sourceIconUrl TEXT, threadUrl TEXT, savedAt INTEGER NOT NULL,
                screenshotPaths TEXT NOT NULL,
                PRIMARY KEY(sourceId, threadId)
            )
            """.trimIndent(),
        )
    }

    private fun createVersion5Table(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE post_read_states (
                sourceId TEXT NOT NULL,
                threadId TEXT NOT NULL,
                postId TEXT NOT NULL,
                readAt INTEGER NOT NULL,
                PRIMARY KEY(sourceId, threadId, postId)
            )
            """.trimIndent(),
        )
    }

    private fun addVersion6Columns(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_history ADD COLUMN sourceName TEXT")
        db.execSQL("ALTER TABLE reading_history ADD COLUMN boardName TEXT")
        db.execSQL("ALTER TABLE saved_posts ADD COLUMN sourceName TEXT")
        db.execSQL("ALTER TABLE saved_posts ADD COLUMN boardName TEXT")
    }

    private fun migrateSavedPostsToVersion7(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE saved_posts_v7 (
                sourceId TEXT NOT NULL, sourceName TEXT, threadId TEXT NOT NULL,
                boardUrl TEXT NOT NULL, boardName TEXT, title TEXT, author TEXT,
                createdAt INTEGER, commentCount INTEGER, replyCount INTEGER,
                thumbnail TEXT, rawImage TEXT, previewContent TEXT NOT NULL,
                sourceIconUrl TEXT, threadUrl TEXT, savedAt INTEGER NOT NULL,
                screenshotAssetRefs TEXT NOT NULL,
                PRIMARY KEY(sourceId, threadId)
            )
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE saved_posts")
        db.execSQL("ALTER TABLE saved_posts_v7 RENAME TO saved_posts")
    }

    private companion object {
        const val TEST_DB = "collection-migration-test.db"
    }
}
