package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        )

        assertEquals(ResourceModel(handle), resourceModelOrNull(handle.asModel()))
        assertNull(resourceModelOrNull("https://example.com/image.png"))
        assertNull(resourceModelOrNull("file:///data/data/secret"))
        assertNull(resourceModelOrNull("content://provider/secret"))
        assertNull(resourceModelOrNull("newshub-resource://0123456789abcdef/0/abcdefghijklmnopqrstuvwxyzABCDEF"))
    }

    @Test
    fun `diagnostic string never exposes capability token or serialized model`() {
        val token = "abcdefghijklmnopqrstuvwxyzABCDEF"
        val handle = ResourceHandle("0123456789abcdef", 7, token)

        val diagnostic = ResourceModel(handle).toString()

        assertFalse(diagnostic.contains(token))
        assertFalse(diagnostic.contains(handle.asModel()))
        assertEquals(
            "ResourceModel(sourceSession=0123456789abcdef, generation=7, token=REDACTED)",
            diagnostic,
        )
    }
}
