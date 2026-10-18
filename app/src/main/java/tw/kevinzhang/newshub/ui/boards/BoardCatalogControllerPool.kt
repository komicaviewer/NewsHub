package tw.kevinzhang.newshub.ui.boards

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.model.BoardQuery
import tw.kevinzhang.newshub.ui.collection.BoardCatalogCache

/** Reconcile only changed source/context pairs so an unrelated login preserves other searches. */
class BoardCatalogControllerPool(
    private val cache: BoardCatalogCache,
    private val scope: CoroutineScope,
    private val onInvalidated: (Source, SourceFailure) -> Unit = { _, _ -> },
    private val onChanged: () -> Unit = {},
) {
    private data class Entry(val source: Source, val context: BoardCatalogContext, val controller: BoardCatalogController, val watcher: Job)
    private val entries = mutableMapOf<String, Entry>()
    val controllers: Map<String, BoardCatalogController> get() = entries.mapValues { it.value.controller }
    fun reconcile(sources: List<Source>, contexts: Map<String, BoardCatalogContext>, query: (String) -> BoardQuery = { BoardQuery() }) {
        val byId = sources.associateBy(Source::id)
        entries.keys.toList().forEach { id ->
            val entry = entries.getValue(id)
            if (entry.source !== byId[id] || entry.context != contexts[id]) {
                entries.remove(id); entry.watcher.cancel(); entry.controller.close()
            }
        }
        sources.forEach { source ->
            val context = contexts[source.id] ?: return@forEach
            if (source.id in entries) return@forEach
            val controller = BoardCatalogController(source, context, cache, scope) { onInvalidated(source, it) }
            controller.setQuery(query(source.id))
            val watcher = scope.launch { controller.state.collect { onChanged() } }
            entries[source.id] = Entry(source, context, controller, watcher)
        }
        onChanged()
    }
    fun close() {
        entries.values.forEach { it.watcher.cancel(); it.controller.close() }
        entries.clear()
    }
}
