package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUrlSelectorTest {

    @Test
    fun `uses thumbnail when raw images are not required`() {
        assertEquals(
            "https://example.com/thumb.jpg",
            selectImageUrl(
                raw = "https://example.com/raw.jpg",
                thumb = "https://example.com/thumb.jpg",
                alwaysUseRawImage = false,
            ),
        )
    }

    @Test
    fun `falls back to raw when thumbnail is missing`() {
        assertEquals(
            "https://example.com/raw.jpg",
            selectImageUrl(
                raw = "https://example.com/raw.jpg",
                thumb = null,
                alwaysUseRawImage = false,
            ),
        )
    }

    @Test
    fun `uses raw image when source requires it`() {
        assertEquals(
            "https://example.com/raw.jpg",
            selectImageUrl(
                raw = "https://example.com/raw.jpg",
                thumb = "https://example.com/thumb.jpg",
                alwaysUseRawImage = true,
            ),
        )
    }
}
