package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.ExternalLinkHandle

class ExternalLinkHandleTest {
    private val handle = ExternalLinkHandle("0123456789abcdef", 7, "a".repeat(32))

    @Test fun `opens only a parsed Host handle and consumes it once`() {
        var consumeCount = 0
        var opened: String? = null
        val result = openExternalLink(
            handleModel = handle.asModel(),
            consume = {
                consumeCount += 1
                check(consumeCount == 1) { "revoked" }
                "https://news.example/thread/1"
            },
            openUri = { opened = it },
        )
        assertTrue(result)
        assertEquals(1, consumeCount)
        assertEquals("https://news.example/thread/1", opened)

        assertFalse(openExternalLink(handle.asModel(), { error("revoked") }, { error("must not open") }))
    }

    @Test fun `naked URL never reaches URI handler`() {
        var called = false
        val result = openExternalLink(
            handleModel = "https://news.example/thread/1",
            consume = { error("must not consume") },
            openUri = { called = true },
        )
        assertFalse(result)
        assertFalse(called)
    }
}
