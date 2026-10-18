package tw.kevinzhang.marketplace

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/** Own the file before switching dispatcher: prompt cancellation can discard an IO result. */
internal suspend fun repositoryFileOperation(file: File, dispatcher: CoroutineDispatcher,
    action: suspend () -> File): File = try {
    withContext(dispatcher) { action() }
} catch (error: Throwable) {
    file.delete()
    throw error
}
