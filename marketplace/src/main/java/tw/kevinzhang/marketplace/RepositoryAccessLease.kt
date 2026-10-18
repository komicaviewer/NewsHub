package tw.kevinzhang.marketplace

import kotlinx.coroutines.*

/** Cancels a complete operation, including metadata verification and installer handoff, on logout. */
suspend fun <T> RepositoryAccessCredential.authorized(action: suspend () -> T): T = coroutineScope {
    ensureCurrent()
    val current = currentCoroutineContext()[Job]!!
    val handle = lifetime?.invokeOnCompletion { current.cancel(CancellationException("Repository login ended")) }
    try {
        ensureActive()
        val result = action()
        ensureCurrent(); ensureActive()
        result
    } finally { handle?.dispose() }
}
