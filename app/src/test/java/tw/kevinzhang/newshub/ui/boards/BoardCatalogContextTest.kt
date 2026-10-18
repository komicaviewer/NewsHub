package tw.kevinzhang.newshub.ui.boards

import org.junit.Assert.*
import org.junit.Test

class BoardCatalogContextTest {
    @Test fun `credential mutation suspends publication until the new credentials are installed`() {
        val registry = BoardCatalogContexts()
        val before = registry.activate("forum", "id")
        var cleared = false
        registry.mutate("forum") {
            assertFalse(registry.states.value.containsKey("forum"))
            cleared = true
        }
        assertTrue(cleared)
        assertNotEquals(before.epoch, registry.states.value.getValue("forum").epoch)
        registry.suspendContext("forum")
        assertTrue(registry.states.value.isEmpty())
        registry.resume("forum")
        assertEquals("id", registry.states.value.getValue("forum").identityKey)
    }
    @Test fun `same account state can rotate context and revoke old responses`() {
        val registry = BoardCatalogContexts()
        val first = registry.activate("forum", "identity-a")
        val next = registry.rotate("forum")
        assertNotEquals(first.epoch, next?.epoch)
        assertEquals("identity-a", next?.identityKey)
        registry.remove("forum")
        assertTrue(registry.states.value.isEmpty())
    }
    @Test fun `same source id under another identity has a separate context`() {
        val registry = BoardCatalogContexts()
        val first = registry.activate("forum", "identity-a")
        val second = registry.activate("forum", "identity-b")
        assertNotEquals(first, second)
    }
}
