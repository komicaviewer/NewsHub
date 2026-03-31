package tw.kevinzhang.komica_api.request.sora

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SoraBoardRequestBuilderTest {

    @Test
    fun `Test setPage with url expect successful`() {
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
    fun `Test setPage with url with page_num expect successful`() {
        val req = SoraBoardRequestBuilder()
            .setUrl("https://gaia.komica.org/00/2.htm".toHttpUrl())
            .setPage(1)
            .build()
        assertEquals(
            "https://gaia.komica.org/00/1.htm".toHttpUrl(),
            req.url
        )
    }
}