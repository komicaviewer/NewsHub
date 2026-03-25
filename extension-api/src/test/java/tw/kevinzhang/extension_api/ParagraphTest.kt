package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.rawImages

class ParagraphTest {
    @Test fun `rawImages returns only image paragraphs`() {
        val paragraphs = listOf(
            Paragraph.Text("hello"),
            Paragraph.ImageInfo(thumb = "t.jpg", raw = "r.jpg"),
            Paragraph.Quote("q"),
            Paragraph.ImageInfo(raw = "r2.jpg"),
        )
        assertEquals(listOf("r.jpg", "r2.jpg"), paragraphs.rawImages())
    }
}
