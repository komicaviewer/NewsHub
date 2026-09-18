package tw.kevinzhang.extension_loader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRefreshCompletionTest {
    @Test fun `superseded refresh waits until replacement publishes instead of returning empty sources`() = runBlocking {
        val completion = ExtensionRefreshCompletion()
        val requested = completion.request()
        val replacement = completion.request()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            completion.await(requested, 5_000) {
                // The manager drops a scan whose generation became stale before taking its mutex.
                assertFalse(completion.isCurrent(requested))
            }
        }
        assertFalse(waiter.isCompleted)
        completion.published(requested)
        yield()
        assertFalse(waiter.isCompleted)
        completion.published(replacement)
        waiter.await()
    }

    @Test fun `refresh superseded during scan also waits for replacement publication`() = runBlocking {
        val completion = ExtensionRefreshCompletion()
        val requested = completion.request()
        val scanStarted = CompletableDeferred<Unit>()
        val finishScan = CompletableDeferred<Unit>()
        val waiter = async {
            completion.await(requested, 5_000) {
                scanStarted.complete(Unit)
                finishScan.await()
                if (completion.isCurrent(requested)) completion.published(requested)
            }
        }
        scanStarted.await()
        val replacement = completion.request()
        finishScan.complete(Unit)
        yield()
        assertFalse(waiter.isCompleted)
        completion.published(replacement)
        waiter.await()
    }

    @Test fun `already published scan completes without another emission`() = runBlocking {
        val completion = ExtensionRefreshCompletion()
        val requested = completion.request()
        completion.published(requested)
        completion.await(requested, 5_000) {}
    }

    @Test fun `missing replacement publication has a finite timeout`() = runBlocking {
        val completion = ExtensionRefreshCompletion()
        val requested = completion.request()
        completion.request()
        val failure = runCatching { completion.await(requested, 25) {} }.exceptionOrNull()
        assertTrue(failure is TimeoutCancellationException)
    }
}
