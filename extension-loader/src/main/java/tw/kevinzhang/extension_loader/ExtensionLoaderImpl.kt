package tw.kevinzhang.extension_loader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tw.kevinzhang.extension_api.Source
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionLoaderImpl private constructor(
    discoveredSources: Flow<List<Source>>,
    initialSources: List<Source>,
) : ExtensionLoader {
    @Inject
    constructor(extensionManager: ExtensionManager) : this(
        discoveredSources = extensionManager.installedExtensions.map { bundles ->
            bundles.flatMap { it.sources }
        },
        initialSources = emptyList(),
    )

    internal constructor(sources: List<Source>) : this(flowOf(sources), sources)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val sourcesFlow: StateFlow<List<Source>> = discoveredSources
        .map { sources ->
            // A collision is a security event, not a scan-order tie-breaker. The manager already
            // quarantines discovered collisions; this second boundary protects injected callers.
            val duplicates = sources.groupBy(Source::id).filterValues { it.size != 1 }.keys
            sources.filterNot { it.id in duplicates }
        }
        .stateIn(scope, SharingStarted.Eagerly, initialSources)

    override fun getAllSources(): List<Source> = sourcesFlow.value

    override fun getSource(id: String): Source? = getAllSources().singleOrNull { it.id == id }
}
