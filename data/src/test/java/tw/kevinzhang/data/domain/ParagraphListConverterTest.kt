package tw.kevinzhang.data.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.RichTextColor
import tw.kevinzhang.extension_api.model.RichTextEmphasis
import tw.kevinzhang.extension_api.model.RichTextLayout
import tw.kevinzhang.extension_api.model.RichTextRun

class ParagraphListConverterTest {
    @Test
    fun `round trips rich text formatting metadata`() {
        val paragraphs = listOf(
            Paragraph.RichText(
                runs = listOf(
                    RichTextRun(
                        text = "綠字",
                        color = RichTextColor.GREEN,
                        emphasis = RichTextEmphasis.BRIGHT,
                        linkUrl = "https://example.com",
                    ),
                    RichTextRun("\n保留空白"),
                ),
                layout = RichTextLayout.PREFORMATTED_WRAP,
            ),
        )

        assertEquals(paragraphs, ParagraphListConverter().fromJson(ParagraphListConverter().toJson(paragraphs)))
    }
}
