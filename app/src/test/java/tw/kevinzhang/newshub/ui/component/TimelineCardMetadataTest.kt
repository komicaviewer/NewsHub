package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineCardMetadataTest {
    @Test
    fun `keeps persisted names and uses the installed source raw image policy`() {
        val metadata = timelineCardMetadata(
            sourceId = "ptt",
            sourceName = "PTT 批踢踢實業坊",
            boardName = "Gossiping",
            rawImageSourceIds = setOf("ptt"),
        )

        assertEquals("PTT 批踢踢實業坊", metadata.sourceName)
        assertEquals("Gossiping", metadata.boardName)
        assertTrue(metadata.alwaysUseRawImage)
    }

    @Test
    fun `does not use raw images for a source outside the installed policy`() {
        val metadata = timelineCardMetadata(
            sourceId = "ptt",
            sourceName = null,
            boardName = null,
            rawImageSourceIds = setOf("komica"),
        )

        assertFalse(metadata.alwaysUseRawImage)
    }
}
