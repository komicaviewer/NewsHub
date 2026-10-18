package tw.kevinzhang.newshub.ui.boards

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BoardCatalogContext(val identityKey: String, val epoch: String, val persistAllowed: Boolean = false)

/** Generations also distinguish two logins with the same AuthState. */
class BoardCatalogContexts {
    private val mutableStates = MutableStateFlow<Map<String, BoardCatalogContext>>(emptyMap())
    private val identities = mutableMapOf<String, String>()
    val states = mutableStates.asStateFlow()
    @Synchronized fun activate(sourceId: String, identityKey: String, enabled: Boolean = true): BoardCatalogContext =
        BoardCatalogContext(identityKey, UUID.randomUUID().toString()).also {
            identities[sourceId] = identityKey
            mutableStates.value = if (enabled) mutableStates.value + (sourceId to it) else mutableStates.value - sourceId
        }
    @Synchronized fun rotate(sourceId: String): BoardCatalogContext? =
        mutableStates.value[sourceId]?.let { activate(sourceId, it.identityKey) }
    @Synchronized fun remove(sourceId: String) {
        identities.remove(sourceId)
        mutableStates.value = mutableStates.value - sourceId
    }
    @Synchronized fun suspendContext(sourceId: String) {
        mutableStates.value = mutableStates.value - sourceId
    }
    @Synchronized fun resume(sourceId: String): BoardCatalogContext? =
        identities[sourceId]?.let { activate(sourceId, it) }

    /** Never publish a new generation until its credential mutation has completed. */
    @Synchronized fun mutate(sourceId: String, action: () -> Unit) {
        suspendContext(sourceId)
        action()
        resume(sourceId)
    }
}
