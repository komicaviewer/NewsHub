package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.extension_api.ResourceHandle

class ResourceModelTest {
    @Test
    fun `only a well formed host resource capability is accepted`() {
        val handle = ResourceHandle(
            sourceSession = "0123456789abcdef",
            generation = 7,
            token = "abcdefghijklmnopqrstuvwxyzABCDEF",
        ).asModel()

        assertEquals(handle, resourceModelOrNull(handle))
        assertNull(resourceModelOrNull("https://example.com/image.png"))
        assertNull(resourceModelOrNull("file:///data/data/secret"))
        assertNull(resourceModelOrNull("content://provider/secret"))
        assertNull(resourceModelOrNull("newshub-resource://0123456789abcdef/0/abcdefghijklmnopqrstuvwxyzABCDEF"))
    }
}
