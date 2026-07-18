package tw.kevinzhang.newshub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tw.kevinzhang.data.CollectionRepository
import tw.kevinzhang.extension_api.TwocatSourceIds
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Starts the data move only after an actual current twocat extension has been discovered. */
@Singleton
class TwocatMigrationCoordinator @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val collectionRepository: CollectionRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            extensionLoader.sourcesFlow
                .filter { sources -> sources.any { it.id == TwocatSourceIds.CURRENT } }
                .map {
                    runCatching {
                        collectionRepository.migrateSourceIds(
                            legacySourceIds = TwocatSourceIds.legacyIds,
                            currentSourceId = TwocatSourceIds.CURRENT,
                        )
                    }.isSuccess
                }
                // A successful first migration completes this collector. Failures do not block
                // startup and remain subscribed for the next extension refresh to retry.
                .first { it }
        }
    }
}
