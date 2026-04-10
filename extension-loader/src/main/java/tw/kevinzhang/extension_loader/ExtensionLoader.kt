package tw.kevinzhang.extension_loader

import kotlinx.coroutines.flow.StateFlow
import tw.kevinzhang.extension_api.Source

interface ExtensionLoader {
    /** Reactive list of all available sources (built-in + installed extensions). Updates on install/uninstall. */
    val sourcesFlow: StateFlow<List<Source>>

    /** Returns the current source list synchronously. Equivalent to sourcesFlow.value. */
    fun getAllSources(): List<Source>

    fun getSource(id: String): Source?
}
