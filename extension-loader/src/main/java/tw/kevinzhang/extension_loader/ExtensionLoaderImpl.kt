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
import okhttp3.OkHttpClient
import tw.kevinzhang.extension_api.SessionAwareSource
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceRuntimeProvider
import tw.kevinzhang.extension_api.TwocatSourceIds
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionLoaderImpl private constructor(
    private val okHttpClient: OkHttpClient,
    private val runtimeProvider: SourceRuntimeProvider,
    discoveredSources: Flow<List<Source>>,
    initialSources: List<Source>,
) : ExtensionLoader {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        runtimeProvider: SourceRuntimeProvider,
        extensionManager: ExtensionManager,
    ) : this(
        okHttpClient = okHttpClient,
        runtimeProvider = runtimeProvider,
        discoveredSources = extensionManager.installedExtensions.map { installed ->
            installed.flatMap { it.sources }
        },
        initialSources = emptyList(),
    )

    internal constructor(
        okHttpClient: OkHttpClient,
        runtimeProvider: SourceRuntimeProvider,
        sources: List<Source>,
    ) : this(okHttpClient, runtimeProvider, flowOf(sources), sources)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val sourcesFlow: StateFlow<List<Source>> = discoveredSources
        .map(::attach)
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = attach(initialSources),
        )

    private fun attach(sources: List<Source>): List<Source> = deduplicateTwocatSources(sources).onEach { source ->
        if (source is SessionAwareSource) {
            source.onAttach(runtimeProvider.runtimeFor(source.id))
        } else {
            source.onAttach(okHttpClient)
        }
    }

    override fun getAllSources(): List<Source> = sourcesFlow.value

    override fun getSource(id: String): Source? {
        val sources = getAllSources()
        return sources.find { it.id == id }
            ?: if (id in TwocatSourceIds.legacyIds) {
                sources.find { TwocatSourceIds.isTwocatId(it.id) }
            } else {
                null
            }
    }

    /**
     * Android treats a renamed extension application ID as a separate package, so an old and
     * a current twocat APK can be installed at the same time. Keep a single source visible and
     * prefer the current ID; if it is not installed, retain the newest legacy ID as a bridge.
     */
    private fun deduplicateTwocatSources(sources: List<Source>): List<Source> {
        val twocatSources = sources.filter { TwocatSourceIds.isTwocatId(it.id) }
        if (twocatSources.size < 2) return sources

        val chosen = twocatSources.firstOrNull { it.id == TwocatSourceIds.CURRENT }
            ?: twocatSources.firstOrNull { it.id == TwocatSourceIds.LEGACY }
            ?: twocatSources.first()
        var inserted = false
        return sources.mapNotNull { source ->
            if (!TwocatSourceIds.isTwocatId(source.id)) source
            else if (!inserted) {
                inserted = true
                chosen
            } else {
                null
            }
        }
    }
}
