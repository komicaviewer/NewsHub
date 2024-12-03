package tw.kevinzhang.komica_api.parser._2cat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class _2catUrlParserTest {

    @Test
    fun `Test parseBoardId expect successful`() {
        val parser = _2catUrlParser()
        val boardId = parser.parseBoardId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals("granblue", boardId)
    }

    @Test
    fun `Test parsePostId expect successful`() {
        val parser = _2catUrlParser()
        val postId = parser.parsePostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals("23211", postId)
    }

    @Test
    fun `Test parseHeadPostId expect successful`() {
        val parser = _2catUrlParser()
        val postId = parser.parseHeadPostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals("23210", postId)
    }

    @Test
    fun `Test parseRePostId expect successful`() {
        val parser = _2catUrlParser()
        val postId = parser.parsePostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals("23211", postId)
    }

    @Test
    fun `Test parsePage expect successful`() {
        val parser = _2catUrlParser()
        val page = parser.parsePage("https://2cat.org/granblue/?page=1".toHttpUrl())
        assertEquals(1, page)
    }

    @Test
    fun `Test hasBoardId expect successful`() {
        val parser = _2catUrlParser()
        val hasBoardId = parser.hasBoardId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasBoardId)
    }

    @Test
    fun `Test hasPostId expect successful`() {
        val parser = _2catUrlParser()
        val hasPostId = parser.hasPostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasHeadPostId expect successful`() {
        val parser = _2catUrlParser()
        val hasPostId = parser.hasHeadPostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasRePostId expect successful`() {
        val parser = _2catUrlParser()
        val hasPostId = parser.hasRePostId("https://2cat.org/granblue/?res=23210#r23211".toHttpUrl())
        assertEquals(true, hasPostId)
    }

    @Test
    fun `Test hasPage expect successful`() {
        val parser = _2catUrlParser()
        val hasPage = parser.hasPage("https://2cat.org/granblue/?page=1".toHttpUrl())
        assertEquals(true, hasPage)
    }
}