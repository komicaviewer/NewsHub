package tw.kevinzhang.newshub.ui.boards

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BoardCatalogContext(val identityKey: String, val epoch: String, val persistAllowed: Boolean = false)

/** Generations also distinguish two logins with the same AuthState. */
class BoardCatalogContexts {
    private val mutableStates = MutableStateFlow<Map<String, BoardCatalogContext>>(emptyMap())
    val states = mutableStates.asStateFlow()
    @Synchronized fun activate(sourceId: String, identityKey: String): BoardCatalogContext =
        BoardCatalogContext(identityKey, UUID.randomUUID().toString()).also {
            mutableStates.value = mutableStates.value + (sourceId to it)
        }
    @Synchronized fun rotate(sourceId: String): BoardCatalogContext? =
        mutableStates.value[sourceId]?.let { activate(sourceId, it.identityKey) }
    @Synchronized fun remove(sourceId: String) {
        mutableStates.value = mutableStates.value - sourceId
    }
}
