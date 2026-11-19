package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionProtocolTest {
    @Test
    fun `bind permission is named after the host package`() {
        assertEquals(
            "tw.kevinzhang.newshub.debug.permission.BIND_EXTENSION",
            ExtensionProtocol.bindPermission("tw.kevinzhang.newshub.debug"),
        )
    }

    @Test
    fun `prod bind permission constant matches prod host`() {
        assertEquals(
            ExtensionProtocol.BIND_PERMISSION,
            ExtensionProtocol.bindPermission("tw.kevinzhang.newshub"),
        )
    }
}
