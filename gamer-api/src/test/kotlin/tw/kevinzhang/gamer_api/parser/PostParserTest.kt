package tw.kevinzhang.gamer_api.parser

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tw.kevinzhang.gamer_api.loadFile
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl

internal class PostParserTest {

    private val parser = PostParser(UrlParserImpl())

    // region parse()

    @Test
    fun `Test PostParser expect successful`() {
        val post = parser.parse(
            loadFile("./src/test/html/Post.html")!!.toResponseBody(),
            RequestBuilderImpl().setUrl("https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175&sn=46104650".toHttpUrl()).build(),
        )
        assertEquals("46104650", post.id)
    }

    // endregion

    // region flatDiv()

    @Test
    fun `flatDiv - empty list returns empty list`() {
        // Input:  (empty list)
        // Output: (empty list)
        val result = with(parser) { emptyList<Node>().flatDiv() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flatDiv - non-div element is returned as-is`() {
        // Input:  [<span>hello</span>]
        // Output: [<span>hello</span>]
        val span = Element("span").apply { text("hello") }
        val result = with(parser) { listOf<Node>(span).flatDiv() }
        assertEquals(1, result.size)
        assertEquals(span, result[0])
    }

    @Test
    fun `flatDiv - TextNode is returned as-is`() {
        // Input:  ["hello world" (TextNode)]
        // Output: ["hello world" (TextNode)]
        val textNode = TextNode("hello world")
        val result = with(parser) { listOf<Node>(textNode).flatDiv() }
        assertEquals(1, result.size)
        assertTrue(result[0] is TextNode)
        assertEquals("hello world", (result[0] as TextNode).text())
    }

    @Test
    fun `flatDiv - empty TextNode is returned as-is`() {
        // Input:  ["" (TextNode)]
        // Output: ["" (TextNode)]
        val textNode = TextNode("")
        val result = with(parser) { listOf<Node>(textNode).flatDiv() }
        assertEquals(1, result.size)
        assertTrue(result[0] is TextNode)
    }

    @Test
    fun `flatDiv - div with single non-div child is replaced by that child`() {
        // Input:  [<div><span>inner</span></div>]
        // Output: [<span>inner</span>]
        val span = Element("span").apply { text("inner") }
        val div = Element("div").apply { appendChild(span) }
        val result = with(parser) { listOf<Node>(div).flatDiv() }
        assertEquals(1, result.size)
        assertEquals(span, result[0])
    }

    @Test
    fun `flatDiv - div with TextNode child produces TextNode only`() {
        // Input:  [<div>inside div</div>]
        // Output: ["inside div" (TextNode)]
        val textNode = TextNode("inside div")
        val div = Element("div").apply { appendChild(textNode) }
        val result = with(parser) { listOf<Node>(div).flatDiv() }
        assertEquals(1, result.size)
        assertTrue(result[0] is TextNode)
        assertEquals("inside div", (result[0] as TextNode).text())
    }

    @Test
    fun `flatDiv - empty div contributes no nodes`() {
        // Input:  [<div></div>]
        // Output: (empty list)
        val div = Element("div")
        val result = with(parser) { listOf<Node>(div).flatDiv() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `flatDiv - div with multiple children expands all of them`() {
        // Input:  [<div><span>first</span><span>second</span></div>]
        // Output: [<span>first</span>, <span>second</span>]
        val span1 = Element("span").apply { text("first") }
        val span2 = Element("span").apply { text("second") }
        val div = Element("div").apply {
            appendChild(span1)
            appendChild(span2)
        }
        val result = with(parser) { listOf<Node>(div).flatDiv() }
        assertEquals(2, result.size)
        assertEquals(span1, result[0])
        assertEquals(span2, result[1])
    }

    @Test
    fun `flatDiv - nested div is fully flattened`() {
        // Input:  [<div><div><span>deep</span></div></div>]
        // Output: [<span>deep</span>]
        val span = Element("span").apply { text("deep") }
        val innerDiv = Element("div").apply { appendChild(span) }
        val outerDiv = Element("div").apply { appendChild(innerDiv) }
        val result = with(parser) { listOf<Node>(outerDiv).flatDiv() }
        assertEquals(1, result.size)
        assertEquals(span, result[0])
    }

    @Test
    fun `flatDiv - triple nested div is fully flattened`() {
        // Input:  [<div><div><div><img src="photo.jpg"></div></div></div>]
        // Output: [<img src="photo.jpg">]
        val img = Element("img").apply { attr("src", "photo.jpg") }
        val div3 = Element("div").apply { appendChild(img) }
        val div2 = Element("div").apply { appendChild(div3) }
        val div1 = Element("div").apply { appendChild(div2) }
        val result = with(parser) { listOf<Node>(div1).flatDiv() }
        assertEquals(1, result.size)
        assertEquals(img, result[0])
    }

    @Test
    fun `flatDiv - div with mixed children expands in order`() {
        // Input:  [<div><span>span</span>text<a>link</a></div>]
        // Output: [<span>span</span>, "text" (TextNode), <a>link</a>]
        val span = Element("span").apply { text("span") }
        val textNode = TextNode("text")
        val a = Element("a").apply { text("link") }
        val div = Element("div").apply {
            appendChild(span)
            appendChild(textNode)
            appendChild(a)
        }
        val result = with(parser) { listOf<Node>(div).flatDiv() }
        assertEquals(3, result.size)
        assertEquals(span, result[0])
        assertTrue(result[1] is TextNode)
        assertEquals(a, result[2])
    }

    @Test
    fun `flatDiv - mixed top-level list processes each node independently`() {
        // Input:  [<span>span</span>, "text" (TextNode), <div><span>from div</span></div>]
        // Output: [<span>span</span>, "text" (TextNode), <span>from div</span>]
        val span = Element("span").apply { text("span") }
        val textNode = TextNode("text")
        val innerSpan = Element("span").apply { text("from div") }
        val div = Element("div").apply { appendChild(innerSpan) }
        val result = with(parser) { listOf<Node>(span, textNode, div).flatDiv() }
        assertEquals(3, result.size)
        assertEquals(span, result[0])
        assertTrue(result[1] is TextNode)
        assertEquals(innerSpan, result[2])
    }

    @Test
    fun `flatDiv - multiple TextNodes are each returned as-is`() {
        // Input:  ["line1" (TextNode), "line2" (TextNode)]
        // Output: ["line1" (TextNode), "line2" (TextNode)]
        val t1 = TextNode("line1")
        val t2 = TextNode("line2")
        val result = with(parser) { listOf<Node>(t1, t2).flatDiv() }
        assertEquals(2, result.size)
        assertEquals("line1", (result[0] as TextNode).text())
        assertEquals("line2", (result[1] as TextNode).text())
    }

    @Test
    fun `flatDiv - div containing nested div and TextNode flattens correctly`() {
        // Input:  [<div><div>deep text</div></div>]
        // Output: ["deep text" (TextNode)]
        val textNode = TextNode("deep text")
        val innerDiv = Element("div").apply { appendChild(textNode) }
        val outerDiv = Element("div").apply { appendChild(innerDiv) }
        val result = with(parser) { listOf<Node>(outerDiv).flatDiv() }
        assertEquals(1, result.size)
        assertTrue(result[0] is TextNode)
        assertEquals("deep text", (result[0] as TextNode).text())
    }

    // endregion
}