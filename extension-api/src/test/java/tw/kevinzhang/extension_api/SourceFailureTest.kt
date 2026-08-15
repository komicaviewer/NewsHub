package tw.kevinzhang.extension_api

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SourceFailureTest {
    @Test
    fun `wire format removes unsafe evidence`() {
        val encoded = SourceFailureWire.encode(
            SourceFailure(
                code = SourceFailureCode.HOST_POLICY,
                operation = "board/page?token=secret",
                observedHost = "evil.example/path?token=secret",
                allowedHosts = listOf("GOOD.EXAMPLE", "bad.example/path"),
            ),
        )
        val decoded = SourceFailureWire.decode(encoded)

        assertNull(decoded.operation)
        assertNull(decoded.observedHost)
        assertEquals(listOf("good.example"), decoded.allowedHosts)
        assertFalse(encoded.contains("token"))
        assertFalse(encoded.contains("path"))
    }

    @Test
    fun `malformed wire payload becomes generic runtime failure`() {
        assertEquals(
            SourceFailureCode.EXTENSION_RUNTIME,
            SourceFailureWire.decode("not-json-with-secret").code,
        )
    }

    @Test
    fun `cancellation is never converted to a source failure`() {
        try {
            SourceFailures.fromThrowable(CancellationException("revoked"), "board_page")
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `authentication exception keeps a stable user-action classification`() {
        val failure = SourceFailures.fromThrowable(AuthenticationRequiredException("private server text"), "board_page")

        assertEquals(SourceFailureCode.AUTH_REQUIRED, failure.code)
        assertEquals("board_page", failure.operation)
        assertFalse(SourceFailureWire.encode(failure).contains("private server text"))
    }
}
