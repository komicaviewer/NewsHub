package tw.kevinzhang.extension_loader

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

@RunWith(RobolectricTestRunner::class)
class ExtensionDescriptorTest {

    @Test
    fun `parses a version 1 bundle registry`() {
        val descriptor = ExtensionDescriptorJson.parse(
            """
            {
              "schemaVersion": 1,
              "name": "NewsHub: Komica",
              "sources": [{
                "className": "example.TwocatSource",
                "id": "tw.example.twocat",
                "name": "Twocat",
                "lang": "zh-TW",
                "baseUrl": "https://2cat.org"
              }]
            }
            """.trimIndent(),
        )

        assertEquals("NewsHub: Komica", descriptor.name)
        assertEquals("example.TwocatSource", descriptor.sources.single().className)
    }

    @Test
    fun `rejects empty source list`() {
        assertInvalid("""{"schemaVersion":1,"name":"Bundle","sources":[]}""")
    }

    @Test
    fun `rejects duplicate source ids before loading classes`() {
        assertInvalid(
            """
            {"schemaVersion":1,"name":"Bundle","sources":[
              {"className":"example.One","id":"tw.example.same","name":"One","lang":"zh-TW","baseUrl":"https://one.example"},
              {"className":"example.Two","id":"tw.example.same","name":"Two","lang":"zh-TW","baseUrl":"https://two.example"}
            ]}
            """.trimIndent(),
        )
    }

    @Test
    fun `rejects source runtime metadata that differs from registry`() {
        val registrySource = SourceDescriptor(
            className = "example.Source",
            id = "tw.example.source",
            name = "Registry name",
            lang = "zh-TW",
            baseUrl = "https://example.test",
        )

        try {
            ExtensionDescriptorValidator.validateRuntimeSource(registrySource, fakeSource(name = "Runtime name"))
            fail("Expected source metadata mismatch")
        } catch (_: IllegalArgumentException) {
            // Expected: runtime source metadata must agree with the signed APK registry.
        }
    }

    private fun assertInvalid(json: String) {
        try {
            ExtensionDescriptorJson.parse(json)
            fail("Expected invalid extension descriptor")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun fakeSource(
        id: String = "tw.example.source",
        name: String = "Source",
        language: String = "zh-TW",
    ) = object : Source {
        override val id = id
        override val name = name
        override val language = language
        override val version = 1
        override val iconUrl: String? = null
        override val supportsCommentPagination = false
        override val alwaysUseRawImage = false
        override val needsLogin = false
        override suspend fun getBoardPage(request: BoardPageRequest) = BoardPage(emptyList())
        override suspend fun getThreadSummaries(board: Board, page: Int) = emptyList<ThreadSummary>()
        override suspend fun getThread(summary: ThreadSummary) = Thread("", null, null, emptyList())
    }
}
