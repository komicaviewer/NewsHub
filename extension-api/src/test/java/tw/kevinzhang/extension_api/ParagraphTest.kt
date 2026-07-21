package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.RichTextColor
import tw.kevinzhang.extension_api.model.RichTextLayout
import tw.kevinzhang.extension_api.model.RichTextRun
import tw.kevinzhang.extension_api.model.plainText
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

    @Test fun `rich text exposes lossless plain text for compact consumers`() {
        val paragraph = Paragraph.RichText(
            runs = listOf(
                RichTextRun("綠字", color = RichTextColor.GREEN),
                RichTextRun("\n連結", linkUrl = "https://example.com"),
            ),
            layout = RichTextLayout.PREFORMATTED_WRAP,
        )

        assertEquals("綠字\n連結", paragraph.plainText())
    }
}
