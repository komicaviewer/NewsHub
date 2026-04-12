package tw.kevinzhang.extension_loader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import tw.kevinzhang.extension_api.Source
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ExtensionLoaderImpl @Inject constructor(
    @Named("builtInSources") private val builtInSources: List<@JvmSuppressWildcards Source>,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val extensionManager: ExtensionManager,
) : ExtensionLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val sourcesFlow: StateFlow<List<Source>> = extensionManager.installedExtensions
        .map { installed ->
            val extensionSources = installed.flatMap { it.sources }
            (builtInSources + extensionSources).onEach { it.onAttach(okHttpClient) }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = builtInSources.onEach { it.onAttach(okHttpClient) },
        )

    override fun getAllSources(): List<Source> = sourcesFlow.value

    override fun getSource(id: String): Source? = getAllSources().find { it.id == id }
}
