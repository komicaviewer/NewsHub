package tw.kevinzhang.marketplace

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

class RepositoryFileOperationTest {
    @Test fun `cancelled IO handoff deletes file before result can be claimed`() = runBlocking {
        val file = File.createTempFile("repo-download-", ".apk")
        val owner = Job()
        try {
            withContext(owner) {
                repositoryFileOperation(file, Dispatchers.IO) {
                    file.writeText("verified-download")
                    owner.cancel()
                    file
                }
            }
            fail()
        } catch (_: CancellationException) { }
        assertFalse(file.exists())
    }
}
