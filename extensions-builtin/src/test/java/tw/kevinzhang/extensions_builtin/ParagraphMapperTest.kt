package tw.kevinzhang.extensions_builtin

import org.junit.Test
import org.junit.Assert.*
import tw.kevinzhang.extension_api.model.Paragraph as ExtParagraph
import tw.kevinzhang.komica_api.model.KText
import tw.kevinzhang.komica_api.model.KReplyTo

class ParagraphMapperTest {
    @Test fun `KText maps to Paragraph Text`() {
        val result = KText("hello").toExtParagraph()
        assertTrue(result is ExtParagraph.Text)
        assertEquals("hello", (result as ExtParagraph.Text).content)
    }

    @Test fun `KReplyTo maps to Paragraph ReplyTo`() {
        val result = KReplyTo(">>123").toExtParagraph()
        assertTrue(result is ExtParagraph.ReplyTo)
        assertEquals(">>123", (result as ExtParagraph.ReplyTo).targetId)
    }
}
