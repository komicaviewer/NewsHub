package tw.kevinzhang.komica_api.parser.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SoraUrlParserTest {

    @Test
    fun `Test parsePostId expect successful`() {
        val parser = SoraUrlParser()
        val postId = parser.parsePostId("https://sora.komica.org/00/pixmicat.php?res=25208017".toHttpUrl())
        assertEquals("25208017", postId)
    }

    @Test
    fun `Test parseHeadPostId expect successful`() {
        val parser = SoraUrlParser()
        val postId = parser.parseHeadPostId("https://sora.komica.org/00/pixmicat.php?res=23210#r23211".toHttpUrl())
        assertEquals("23210", postId)
    }

    @Test
    fun `Test parseRePostId expect successful`() {
        val parser = SoraUrlParser()
        val postId = parser.parsePostId("https://sora.komica.org/00/pixmicat.php?res=23210#r23211".toHttpUrl())
        assertEquals("23211", postId)
    }

    @Test
    fun `Test parsePage expect successful`() {
        val parser = SoraUrlParser()
        val page = parser.parsePage("https://sora.komica.org/00/1.htm".toHttpUrl())
        assertEquals(1, page)
    }

    @Test
    fun `Test hasPostId expect successful`() {
        val parser = SoraUrlParser()
        val hasPostId = parser.hasPostId("https://sora.komica.org/00/pixmicat.php?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasHeadPostId expect successful`() {
        val parser = SoraUrlParser()
        val hasPostId = parser.hasHeadPostId("https://sora.komica.org/00/pixmicat.php?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasRePostId expect successful`() {
        val parser = SoraUrlParser()
        val hasPostId = parser.hasRePostId("https://sora.komica.org/00/pixmicat.php?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasPage expect successful`() {
        val parser = SoraUrlParser()
        val hasPostId = parser.hasPage("https://sora.komica.org/00/1.htm".toHttpUrl())
        assertEquals(true, hasPostId)
    }
}