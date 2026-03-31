package tw.kevinzhang.komica_api.request._2cat

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class _2catRequestBuilderTest {

    @Test
    fun `Test setPage expect successful`() {
        val req = _2catRequestBuilder()
            .setUrl("https://2cat.org/granblue".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals("https://2cat.org/granblue?page=1".toHttpUrl(), req.url)
    }

    @Test
    fun `Test setPage with url with page setPage expect successful`() {
        val req = _2catRequestBuilder()
            .setUrl("https://2cat.org/granblue?page=2".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals("https://2cat.org/granblue?page=1".toHttpUrl(), req.url)
    }

    @Test
    fun `Test setRes expect successful`() {
        val req = _2catRequestBuilder()
            .setUrl("https://2cat.org/granblue/?res=963".toHttpUrl())
            .setRes("964")
            .build()
        assertEquals("https://2cat.org/granblue/?res=964".toHttpUrl(), req.url)
    }

    @Test
    fun `Test setFragment expect successful`() {
        val req = _2catRequestBuilder()
            .setUrl("https://2cat.org/granblue/?res=963#r23587".toHttpUrl())
            .setFragment("r23588")
            .build()
        assertEquals("https://2cat.org/granblue/?res=963#r23588".toHttpUrl(), req.url)
    }
}