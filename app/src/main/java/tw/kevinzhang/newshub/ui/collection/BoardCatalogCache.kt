package tw.kevinzhang.newshub.ui.collection

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardQuery
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_FILE = "board_catalog_recent_results"
private const val CACHE_JSON_KEY = "entries"
private const val CATEGORY_CACHE_JSON_KEY = "categories"
private const val MAX_CACHE_ENTRIES = 20
private const val MAX_BOARDS_PER_ENTRY = 90

internal data class CachedBoardCatalogEntry(
    val sourceId: String,
    val query: String,
    val categoryId: String?,
    val boards: List<Board>,
    val updatedAtMillis: Long,
)

internal data class CachedBoardCategories(
    val sourceId: String,
    val categories: List<BoardCategory>,
    val updatedAtMillis: Long,
)

/** A small persistent fallback for recent results; it never attempts to mirror a full catalog. */
@Singleton
class BoardCatalogCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val preferences = context.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE)
    private val entryListType = object : TypeToken<List<CachedBoardCatalogEntry>>() {}.type
    private val categoryListType = object : TypeToken<List<CachedBoardCategories>>() {}.type

    suspend fun get(sourceId: String, query: BoardQuery): List<Board> = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            readEntries().firstOrNull {
                it.sourceId == sourceId &&
                    it.query == query.text.trim() &&
                    it.categoryId == query.categoryId
            }?.boards.orEmpty()
        }
    }

    suspend fun put(sourceId: String, query: BoardQuery, boards: List<Board>) {
        if (boards.isEmpty()) return
        withContext(Dispatchers.IO) {
            synchronized(preferences) {
                val normalizedQuery = query.text.trim()
                val retained = readEntries().filterNot {
                    it.sourceId == sourceId &&
                        it.query == normalizedQuery &&
                        it.categoryId == query.categoryId
                }
                val updated = listOf(
                    CachedBoardCatalogEntry(
                        sourceId = sourceId,
                        query = normalizedQuery,
                        categoryId = query.categoryId,
                        boards = boards.distinctBy(Board::url).take(MAX_BOARDS_PER_ENTRY),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                ) + retained.sortedByDescending(CachedBoardCatalogEntry::updatedAtMillis)
                preferences.edit()
                    .putString(CACHE_JSON_KEY, gson.toJson(updated.take(MAX_CACHE_ENTRIES)))
                    .apply()
            }
        }
    }

    suspend fun getCategories(sourceId: String): List<BoardCategory> = withContext(Dispatchers.IO) {
        synchronized(preferences) {
            readCategories().firstOrNull { it.sourceId == sourceId }?.categories.orEmpty()
        }
    }

    suspend fun putCategories(sourceId: String, categories: List<BoardCategory>) {
        withContext(Dispatchers.IO) {
            synchronized(preferences) {
                val retained = readCategories().filterNot { it.sourceId == sourceId }
                val updated = listOf(
                    CachedBoardCategories(
                        sourceId = sourceId,
                        categories = categories.distinctBy(BoardCategory::id),
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                ) + retained.sortedByDescending(CachedBoardCategories::updatedAtMillis)
                preferences.edit()
                    .putString(CATEGORY_CACHE_JSON_KEY, gson.toJson(updated.take(MAX_CACHE_ENTRIES)))
                    .apply()
            }
        }
    }

    private fun readEntries(): List<CachedBoardCatalogEntry> {
        val json = preferences.getString(CACHE_JSON_KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<CachedBoardCatalogEntry>>(json, entryListType).orEmpty()
        }.getOrElse { emptyList() }
    }

    private fun readCategories(): List<CachedBoardCategories> {
        val json = preferences.getString(CATEGORY_CACHE_JSON_KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<CachedBoardCategories>>(json, categoryListType).orEmpty()
        }.getOrElse { emptyList() }
    }
}
