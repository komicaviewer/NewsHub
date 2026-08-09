package tw.kevinzhang.extension_loader

import android.content.pm.ServiceInfo
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.ExtensionProtocol

@RunWith(RobolectricTestRunner::class)
class ExtensionDescriptorTest {
    @Test
    fun `accepts one isolated service with current metadata`() {
        val descriptor = ExtensionDescriptorValidator.fromServiceInfo(validService())
        assertEquals("tw.kevinzhang.newshub.extension.hackernews", descriptor.packageName)
        assertEquals("tw.kevinzhang.newshub.extension.hackernews", descriptor.sourceId)
    }

    @Test
    fun `rejects a service that can run with extension app permissions`() {
        assertInvalid(validService().apply { flags = flags and ServiceInfo.FLAG_ISOLATED_PROCESS.inv() })
    }

    @Test
    fun `rejects service without host signature bind permission`() {
        assertInvalid(validService().apply { permission = null })
    }

    @Test
    fun `rejects legacy or future protocol instead of negotiating fallback`() {
        assertInvalid(validService().apply { metaData.putInt(ExtensionProtocol.META_PROTOCOL, 0) })
        assertInvalid(validService().apply { metaData.putInt(ExtensionProtocol.META_PROTOCOL, 2) })
    }

    @Test
    fun `unknown package and package-source mismatch are outside bootstrap trust root`() {
        assertNull(OfficialExtensionCatalog.policyFor("attacker.package", "tw.kevinzhang.newshub.extension.hackernews"))
        assertNull(OfficialExtensionCatalog.policyFor("tw.kevinzhang.newshub.extension.ptt", "tw.kevinzhang.newshub.extension.hackernews"))
    }

    private fun validService() = ServiceInfo().apply {
        packageName = "tw.kevinzhang.newshub.extension.hackernews"
        name = "$packageName.HackerNewsService"
        processName = "$packageName:hackernews"
        exported = true
        permission = ExtensionProtocol.BIND_PERMISSION
        flags = ServiceInfo.FLAG_ISOLATED_PROCESS
        metaData = Bundle().apply {
            putInt(ExtensionProtocol.META_PROTOCOL, ExtensionProtocol.VERSION)
            putString(ExtensionProtocol.META_SOURCE_ID, "tw.kevinzhang.newshub.extension.hackernews")
            putString(ExtensionProtocol.META_SOURCE_NAME, "Hacker News")
            putString(ExtensionProtocol.META_SOURCE_LANG, "en")
            putString(ExtensionProtocol.META_SOURCE_BASE_URL, "https://news.ycombinator.com")
        }
    }

    private fun assertInvalid(service: ServiceInfo) {
        try {
            ExtensionDescriptorValidator.fromServiceInfo(service)
            fail("Expected invalid Source service")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
