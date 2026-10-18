package tw.kevinzhang.newshub.ui.boards

import org.junit.Assert.*
import org.junit.Test

class BoardCatalogContextTest {
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
