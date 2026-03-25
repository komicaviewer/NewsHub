package tw.kevinzhang.marketplace

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.marketplace.data.ExtensionIndex

class ExtensionInfoTest {
    @Test fun `parses index json correctly`() {
        val json = """
            {"extensions":[{"id":"tw.a","name":"A","version":2,"versionName":"1.0","language":"zh-TW","iconUrl":null,"apkUrl":"http://a.apk"}]}
        """.trimIndent()
        val index = Gson().fromJson(json, ExtensionIndex::class.java)
        assertEquals(1, index.extensions.size)
        assertEquals("tw.a", index.extensions[0].id)
        assertEquals(2L, index.extensions[0].version)
    }
}
