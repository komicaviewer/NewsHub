package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SoraBoardRequestBuilderTest {

    @Test
    fun `Test setPageReq with url expect successful`() {
        val req = SoraBoardRequestBuilder()
            .setUrl("https://gaia.komica.org/00".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals(
            "https://gaia.komica.org/00/1.htm".toHttpUrl(),
            req.url
        )
    }

    @Test
    fun `Test setPageReq with url with page_num expect successful`() {
        val req = SoraBoardRequestBuilder()
            .setUrl("https://gaia.komica.org/00/2.htm".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals(
            "https://gaia.komica.org/00/1.htm".toHttpUrl(),
            req.url
        )
    }

    @Test
    fun `Test setFragment expect successful`() {
        val req = SoraBoardRequestBuilder()
            .setUrl("https://sora.komica.org/00/pixmicat.php?res=25214959".toHttpUrl())
            .setFragment("r23588")
            .build()
        assertEquals("https://sora.komica.org/00/pixmicat.php?res=25214959#r23588".toHttpUrl(), req.url)
    }

    @Test
    fun `Test setRes expect successful`() {
        val req = SoraBoardRequestBuilder()
            .setUrl("https://sora.komica.org/00/pixmicat.php?res=25214959".toHttpUrl())
            .setRes("25214960")
            .build()
        assertEquals("https://sora.komica.org/00/pixmicat.php?res=25214960".toHttpUrl(), req.url)
    }
}