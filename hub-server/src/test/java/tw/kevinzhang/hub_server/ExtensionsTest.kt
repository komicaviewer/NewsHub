package tw.kevinzhang.hub_server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


internal class ExtensionsTest {

    @Test
    fun `Test trySubstring extension with correct page expect successful`() =
        assertEquals("ABCDEFGHIJK", "ABCDEFGHIJKLMNOPQRSTUVWXYZ".trySubstring(0..10))
}