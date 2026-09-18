package tw.kevinzhang.extension_loader

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** A superseded scan is not completion: wait for the replacement to publish its result. */
internal class ExtensionRefreshCompletion {
    private val requested = AtomicLong()
    private val published = MutableStateFlow(0L)

    fun request(): Long = requested.incrementAndGet()

    fun isCurrent(generation: Long): Boolean = generation == requested.get()

    fun published(generation: Long) {
        published.value = generation
    }

    suspend fun await(generation: Long, timeoutMillis: Long, refresh: suspend () -> Unit) {
        withTimeout(timeoutMillis) {
            refresh()
            published.first { it >= generation && isCurrent(it) }
        }
    }
}
