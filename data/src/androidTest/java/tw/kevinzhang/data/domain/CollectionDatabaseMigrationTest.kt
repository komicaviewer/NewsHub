package tw.kevinzhang.data.domain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

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
    fun version1MigratesTo7WithoutLosingCollections() {
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
    }

    @Test
    fun version4MigratesTo7WithoutInterpretingAbsoluteScreenshotPaths() {
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
            "SELECT title, sourceName, boardName, screenshotAssetRefs " +
                "FROM saved_posts WHERE sourceId = ? AND threadId = ?",
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
    fun versions2_5And6AllReachVersion7WithoutDestructiveFallback() {
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
                assertEquals(7, cursor.getInt(0))
            }
            migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM collections").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
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

    private companion object {
        const val TEST_DB = "collection-migration-test.db"
    }
}
