package tw.kevinzhang.komica_api

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tw.kevinzhang.komica_api.model.KBoard


internal class ExtensionsTest {

    @Test
    fun `Test withHttps extension with same schema expect successful`() =
        assertEquals("https://www.google.com", "https://www.google.com".withHttps())

    @Test
    fun `Test withHttps extension with start with double slash expect successful`() =
        assertEquals("https://www.google.com", "//www.google.com".withHttps())

    @Test
    fun `Test withHttps extension expect failure`() {
        assertThrows<ParseException> {
            "/path".withHttps()
        }
    }

    @Test
    fun `Test withHttps extension with host expect successful`() =
        assertEquals("https://www.google.com", "www.google.com".withHttps())

    @Test
    fun `Test withHttps extension with path expect successful`() =
        assertEquals("https://www.google.com/search", "/search".withHttps("https://www.google.com"))

    @Test
    fun `Test withHttps extension with dot path expect successful`() =
        assertEquals("https://www.google.com/./search", "./search".withHttps("https://www.google.com"))

    @Test
    fun `Test withHttp extension with same schema expect successful`() =
        assertEquals("http://example.com", "http://example.com".withHttp())

    @Test
    fun `Test withHttp extension with start with double slash expect successful`() =
        assertEquals("http://example.com", "//example.com".withHttp())

    @Test
    fun `Test withHttp extension expect failure`() {
        assertThrows<ParseException> {
            "/path".withHttp()
        }
    }

    @Test
    fun `Test withHttp extension with host expect successful`() =
        assertEquals("http://example.com", "example.com".withHttp())

    @Test
    fun `Test toFolder extension expect successful`() {
        assertEquals("http://example.com/path", "http://example.com/path/page.php".toFolder())
    }

    @Test
    fun `Test replaceJpnWeekday extension expect successful`() =
        assertEquals("Mon Tue", "月 火".replaceJpnWeekday())

    @Test
    fun `Test replaceChiWeekday extension expect successful`() =
        assertEquals("Mon Tue", "一 二".replaceChiWeekday())

    @Test
    fun `Test toMillTimestamp extension with years with only two digits extension expect successful`() =
        assertEquals(1662795827000L, "22/09/10(Sat) 15:43:47".toMillTimestamp())

    @Test
    fun `Test HttpUrl toKBoard extension with sora url expect successful`() =
        assert("https://gaia.komica.org/00/".toHttpUrl().toKBoard() is KBoard.Sora)

    @Test
    fun `Test HttpUrl Builder setFilename extension with url expect successful`() =
        assertEquals("https://gaia.komica.org/index.htm", "https://gaia.komica.org/00".toHttpUrl().newBuilder().setFilename("index.htm").build().toString())

    @Test
    fun `Test HttpUrl Builder setFilename extension with url end with slash expect successful`() =
        assertEquals("https://gaia.komica.org/00/index.htm", "https://gaia.komica.org/00/".toHttpUrl().newBuilder().setFilename("index.htm").build().toString())

    @Test
    fun `Test HttpUrl Builder setFilename extension with null parameter expect successful`() =
        assertEquals("https://gaia.komica.org/00", "https://gaia.komica.org/00/index.htm".toHttpUrl().newBuilder().setFilename(null).build().toString())

    @Test
    fun `Test HttpUrl Builder addFilename extension expect successful`() =
        assertEquals("https://gaia.komica.org/00/index.htm", "https://gaia.komica.org/00".toHttpUrl().newBuilder().addFilename("index", "htm").build().toString())

    @Test
    fun `Test HttpUrl Builder removeFilename extension expect successful`() =
        assertEquals("https://gaia.komica.org/00", "https://gaia.komica.org/00/index.htm".toHttpUrl().newBuilder().removeFilename("htm").build().toString())

    @Test
    fun `Test HttpUrl Builder isFile extension with url and param file name and file extension expect successful`() =
        assertEquals(true, "https://gaia.komica.org/00/index.htm".toHttpUrl().isFile("htm"))

    @Test
    fun `Test HttpUrl Builder isFile extension expect successful`() =
        assertEquals(true, "https://gaia.komica.org/00/index.htm".toHttpUrl().isFile())

    @Test
    fun `Test Int isZeroOrNull extension with 0 expect successful`() =
        assertEquals(true, 0.isZeroOrNull())

    @Test
    fun `Test Int isZeroOrNull extension with null expect successful`() =
        assertEquals(true, null.isZeroOrNull())
}

