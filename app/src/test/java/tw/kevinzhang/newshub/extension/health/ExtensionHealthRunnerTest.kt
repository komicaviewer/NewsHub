package tw.kevinzhang.newshub.extension.health

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class ExtensionHealthRunnerTest {
    @Test
    fun healthySourcePassesStructuralContractAndReportContainsNoContent() = runBlocking {
        val profile = loadProfile()

        val report = ExtensionHealthRunner().run(profile, listOf(FakeSource()))

        assertEquals(HealthStatus.PASS, report.status)
        assertEquals(3, report.requestCount)
        assertEquals(
            listOf("get_board_page", "get_thread_summaries", "get_thread_page"),
            report.results.single().steps.map(HealthStepResult::operation),
        )
        val json = ExtensionHealthJson.encodeReport(report)
        assertFalse(json.contains("private thread title"))
        assertFalse(json.contains("private post body"))
        assertFalse(json.contains("https://example.com/board"))
    }

    @Test
    fun malformedSummaryIsClassifiedWithoutLeakingExceptionText() = runBlocking {
        val source = FakeSource(summary = FakeSource.summary.copy(id = ""))

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))

        val failure = report.results.single().steps.last()
        assertEquals(HealthStatus.FAIL, report.status)
        assertEquals(HealthFailureClass.PARSER_CONTRACT, failure.failureClass)
        assertTrue(failure.failureFingerprint?.matches(Regex("[a-f0-9]{24}")) == true)
        assertFalse(ExtensionHealthJson.encodeReport(report).contains("Summary fields failed contract"))
    }

    @Test
    fun missingOrWrongRuntimeIdentityNeverCallsSource() = runBlocking {
        val source = FakeSource(sourceIdentity = SourceIdentity("other.extension", "a".repeat(64), "test.source"))

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))

        assertEquals(0, report.requestCount)
        assertEquals("verify_identity", report.results.single().steps.single().operation)
        assertEquals(HealthFailureClass.HOST_RUNTIME, report.results.single().steps.single().failureClass)
    }

    @Test
    fun profileRejectsCredentialBearingOrUntrustedBoardUrl() {
        val fixture = resource("profile-v1.json")
        val credentialBearing = fixture.replace(
            "\"allowedHosts\": [\"example.com\"]",
            "\"allowedHosts\": [\"example.com\"], \"passwordSecret\": \"secret-name\"",
        )
        val userInfoUrl = fixture.replace(
            "\"allowedHosts\": [\"example.com\"]",
            "\"allowedHosts\": [\"example.com\"], \"boardUrl\": \"https://user:password@example.com/a\"",
        )

        assertThrows(RuntimeException::class.java) { ExtensionHealthJson.decodeProfile(credentialBearing) }
        assertThrows(RuntimeException::class.java) { ExtensionHealthJson.decodeProfile(userInfoUrl) }
    }

    @Test
    fun reportFailureHasOnlyStableSafeFields() = runBlocking {
        val secret = "session=super-secret"
        val source = FakeSource(boardFailure = java.io.IOException("HTTP 429 $secret"))

        val report = ExtensionHealthRunner().run(loadProfile(), listOf(source))
        val json = ExtensionHealthJson.encodeReport(report)

        assertEquals(HealthFailureClass.RATE_LIMITED, report.results.single().steps.last().failureClass)
        assertFalse(json.contains(secret))
        assertNull(report.results.single().steps.last().observedCount)
    }

    @Test
    fun slowOperationStopsAtConfiguredTimeout() = runBlocking {
        val profile = loadProfile().copy(operationTimeoutMs = 1_000)

        val report = ExtensionHealthRunner().run(
            profile = profile,
            sources = listOf(FakeSource(boardDelayMs = 1_500)),
        )

        assertEquals(1, report.requestCount)
        assertEquals(HealthFailureClass.TIMEOUT, report.results.single().steps.single().failureClass)
    }

    private fun loadProfile(): ExtensionHealthProfile = ExtensionHealthJson.decodeProfile(resource("profile-v1.json"))

    private fun resource(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("extension-health/$name"),
    ).readText()

    private class FakeSource(
        override val sourceIdentity: SourceIdentity? = SourceIdentity(
            packageName = "test.extension",
            signerSha256 = "a".repeat(64),
            sourceId = "test.source",
        ),
        private val summary: ThreadSummary = Companion.summary,
        private val boardFailure: Throwable? = null,
        private val boardDelayMs: Long = 0,
    ) : Source {
        override val id = "test.source"
        override val name = "Test"
        override val language = "en"
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false

        override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
            boardFailure?.let { throw it }
            if (boardDelayMs > 0) delay(boardDelayMs)
            return BoardPage(listOf(Board(id, "https://example.com/board", "Board")))
        }

        override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> = listOf(summary)

        override suspend fun getThread(summary: ThreadSummary): Thread = Thread(
            id = summary.id,
            url = "https://example.com/thread/1",
            title = summary.title,
            posts = listOf(post),
        )

        companion object {
            val summary = ThreadSummary(
                sourceId = "test.source",
                boardUrl = "https://example.com/board",
                id = "thread-1",
                title = "private thread title",
                author = "author",
                createdAt = 1L,
                commentCount = 0,
                rawImage = null,
                thumbnail = null,
                previewContent = listOf(Paragraph.Text("private preview")),
            )
            val post = Post(
                id = "post-1",
                author = "author",
                createdAt = 1L,
                thumbnail = null,
                content = listOf(Paragraph.Text("private post body")),
                comments = emptyList(),
            )
        }
    }
}
