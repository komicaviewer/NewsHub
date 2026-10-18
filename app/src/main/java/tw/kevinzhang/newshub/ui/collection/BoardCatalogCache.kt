package tw.kevinzhang.newshub.ui.collection

import javax.inject.Inject
import javax.inject.Singleton
import tw.kevinzhang.extension_api.model.*
import tw.kevinzhang.newshub.ui.boards.BoardCatalogContext

data class CachedBoardResult(val boards: List<Board>, val catalogInfo: BoardCatalogInfo?, val storedAtMillis: Long)

/** Limited recent results; all visibility-sensitive data remains in memory. */
@Singleton
class BoardCatalogCache @Inject constructor() {
    private data class Key(val context: BoardCatalogContext, val query: BoardQuery)
    private val entries = linkedMapOf<Key, CachedBoardResult>()
    @Synchronized fun get(context: BoardCatalogContext, query: BoardQuery): CachedBoardResult? =
        entries[Key(context, query.copy(text = query.text.trim()))]
    @Synchronized fun put(context: BoardCatalogContext, query: BoardQuery, result: CachedBoardResult) {
        val key = Key(context, query.copy(text = query.text.trim()))
        entries.remove(key)
        entries[key] = result.copy(boards = result.boards.distinctBy(Board::url).take(90))
        while (entries.size > 20) entries.remove(entries.keys.first())
    }
    @Synchronized fun invalidate(identityKey: String) {
        entries.keys.removeAll { it.context.identityKey == identityKey }
    }
    // Transitional callers without a verified context cannot read or save results.
    suspend fun get(sourceId: String, query: BoardQuery): List<Board> = emptyList()
    suspend fun put(sourceId: String, query: BoardQuery, boards: List<Board>) = Unit
    suspend fun getCategories(sourceId: String): List<BoardCategory> = emptyList()
    suspend fun putCategories(sourceId: String, categories: List<BoardCategory>) = Unit
}
