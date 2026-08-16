package tw.kevinzhang.extension_loader

import android.content.pm.ServiceInfo
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256

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
    fun `rejects isolated service process outside its package private namespace`() {
        assertInvalid(validService().apply { processName = ":shared" })
        assertInvalid(validService().apply { processName = "attacker.package:shared" })
    }

    @Test
    fun `rejects service without host signature bind permission`() {
        assertInvalid(validService().apply { permission = null })
    }

    @Test
    fun `rejects legacy or future protocol instead of negotiating fallback`() {
        assertInvalid(validService().apply { metaData.putInt(ExtensionProtocol.META_PROTOCOL, 0) })
        assertInvalid(validService().apply { metaData.putInt(ExtensionProtocol.META_PROTOCOL, 3) })
    }

    @Test
    fun `protocol v2 rejects every legacy manifest login field`() {
        listOf(
            ExtensionProtocol.META_NEEDS_LOGIN to true,
            ExtensionProtocol.META_LOGIN_URL to "https://login.example.com",
            ExtensionProtocol.META_LOGIN_HOSTS to "login.example.com",
        ).forEach { (key, value) ->
            assertInvalid(validService().apply {
                when (value) {
                    is Boolean -> metaData.putBoolean(key, value)
                    is String -> metaData.putString(key, value)
                }
            })
        }
    }

    @Test
    fun `signed service metadata must match every runtime descriptor field`() {
        val descriptor = ExtensionDescriptorValidator.fromServiceInfo(validService())
        val expected = ExpectedSourceService(
            serviceClassName = descriptor.serviceClassName,
            name = descriptor.name,
            lang = descriptor.lang,
            baseUrl = descriptor.baseUrl,
            protocol = descriptor.protocol,
            policyHash = "a".repeat(64),
        )
        verifyServiceDescriptor(descriptor, expected)
        listOf(
            expected.copy(serviceClassName = "attacker.OtherService"),
            expected.copy(name = "Lookalike"),
            expected.copy(lang = "zh-TW"),
            expected.copy(baseUrl = "https://attacker.example"),
            expected.copy(protocol = expected.protocol + 1),
        ).forEach { mismatch ->
            assertInvalidDescriptor { verifyServiceDescriptor(descriptor, mismatch) }
        }
    }

    @Test
    fun `signed policy hash mismatch fails closed`() {
        val policy = SourceNetworkPolicy(
            exactHosts = setOf("news.ycombinator.com"),
            operations = mapOf(
                NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                    name = NetworkOperations.SOURCE_READ,
                    methods = setOf("GET", "HEAD"),
                    pathPrefixes = setOf("/"),
                    credentialed = true,
                ),
            ),
            namedCapabilities = setOf(
                NamedHostCapabilities.RESOURCE_READ,
                NamedHostCapabilities.EXTERNAL_LINK,
            ),
        )

        verifyExpectedNetworkPolicyHash(
            policy.sha256(),
            policy,
        )
        assertInvalidDescriptor {
            verifyExpectedNetworkPolicyHash("0".repeat(64), policy)
        }
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

    private fun assertInvalidDescriptor(block: () -> Unit) {
        try {
            block()
            fail("Expected signed descriptor mismatch")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
