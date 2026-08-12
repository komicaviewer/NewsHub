package tw.kevinzhang.newshub.extension

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256
import tw.kevinzhang.extension_loader.ExpectedSourceService
import tw.kevinzhang.extension_loader.ExtensionSigningPolicy
import tw.kevinzhang.extension_loader.VerifiedExtensionTrustSnapshot
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ExtensionIsolationE2ETest {
    @Test
    fun liveHealthFixturePinsTheActuallyInstalledSingleSigner() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageName = "tw.kevinzhang.newshub.extension.hackernews"
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SERVICES or PackageManager.GET_META_DATA,
        )
        val actualSigner = installedSignerSha256(packageInfo)
        val policy = snapshot(
            context,
            targetsVersion = 1,
            validPins = true,
            validContent = true,
            sourceIds = setOf("tw.kevinzhang.newshub.extension.hackernews"),
            pinInstalledSigner = true,
        ).policies.single()

        assertEquals(setOf(actualSigner), policy.approvedCurrentSignersSha256)
        assertEquals(setOf(actualSigner), policy.lineageAnchorsSha256)
    }

    @Test
    fun wrongSignersAreQuarantinedThenThresholdTrustedServicesBindAndExecute() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            ExtensionManagementEntryPoint::class.java,
        )
        entryPoint.trustProvider().clear()

        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(context, targetsVersion = 1, validPins = false, validContent = true),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(15_000) {
            while (entryPoint.manager().quarantinedExtensions.value.size < SOURCE_POLICIES.size) delay(50)
        }
        assertTrue(entryPoint.manager().installedExtensions.value.isEmpty())

        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(context, targetsVersion = 2, validPins = true, validContent = false),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        assertTrue(entryPoint.manager().installedExtensions.value.isEmpty())
        assertEquals(SOURCE_POLICIES.size, entryPoint.manager().quarantinedExtensions.value.size)

        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(context, targetsVersion = 3, validPins = true, validContent = true),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(20_000) {
            while (entryPoint.loader().sourcesFlow.value.size != SOURCE_POLICIES.size) delay(50)
        }

        val installed = entryPoint.manager().installedExtensions.value
        assertEquals(7, installed.size)
        assertEquals(SOURCE_POLICIES.size, installed.sumOf { it.sources.size })
        assertTrue(entryPoint.manager().quarantinedExtensions.value.isEmpty())

        // Sora categories are in-memory constants. A successful response proves the explicit
        // Binder/PFD protocol executed extension code without relying on external network state.
        val sora = entryPoint.loader().getSource("tw.kevinzhang.komica.sora")
            ?: error("Trusted Sora Source was not loaded")
        val categories = withTimeout(10_000) { sora.getBoardCategories() }
        assertTrue(categories.isNotEmpty())
        assertEquals("tw.kevinzhang.newshub.extension.komica", sora.sourceIdentity?.packageName)
    }

    @Suppress("DEPRECATION")
    internal fun snapshot(
        context: Context,
        targetsVersion: Long,
        validPins: Boolean,
        validContent: Boolean,
        sourceIds: Set<String> = SOURCE_POLICIES.mapTo(linkedSetOf()) { it.sourceId },
        pinInstalledSigner: Boolean = false,
    ): VerifiedExtensionTrustSnapshot {
        val selectedPolicies = SOURCE_POLICIES.filter { it.sourceId in sourceIds }
        require(selectedPolicies.mapTo(linkedSetOf()) { it.sourceId } == sourceIds) {
            "Unknown Source requested by health profile"
        }
        val packagePolicies = selectedPolicies.groupBy { it.packageName }.map { (packageName, sources) ->
            val packageInfo = context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SERVICES or
                    PackageManager.GET_META_DATA,
            )
            val baseApk = File(requireNotNull(packageInfo.applicationInfo).sourceDir)
            val packageServices = requireNotNull(packageInfo.services)
            val installedSigner = installedSignerSha256(packageInfo)
            val pin = if (validPins) {
                if (pinInstalledSigner) installedSigner else SIGNER_PINS.getValue(packageName)
            } else {
                "0".repeat(64)
            }
            ExtensionSigningPolicy(
                packageName = packageName,
                expectedVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                targetLength = baseApk.length(),
                targetSha256 = if (validContent) baseApk.sha256() else "0".repeat(64),
                lineageAnchorsSha256 = setOf(pin),
                approvedCurrentSignersSha256 = setOf(pin),
                sources = sources.associate { source ->
                    val service = packageServices.single { service ->
                        service.metaData?.getString(ExtensionProtocol.META_SOURCE_ID) == source.sourceId
                    }
                    val metadata = requireNotNull(service.metaData)
                    source.sourceId to ExpectedSourceService(
                        serviceClassName = service.name,
                        name = requireNotNull(metadata.getString(ExtensionProtocol.META_SOURCE_NAME)),
                        lang = requireNotNull(metadata.getString(ExtensionProtocol.META_SOURCE_LANG)),
                        baseUrl = requireNotNull(metadata.getString(ExtensionProtocol.META_SOURCE_BASE_URL)),
                        protocol = metadata.getInt(ExtensionProtocol.META_PROTOCOL),
                        policyHash = source.policy.sha256(),
                    )
                },
            )
        }
        return VerifiedExtensionTrustSnapshot(
            rootVersion = 1,
            targetsVersion = targetsVersion,
            expiresAtEpochMillis = 1_900_000_000_000L,
            policies = packagePolicies,
        )
    }

    private fun File.sha256(): String = inputStream().buffered().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun installedSignerSha256(packageInfo: android.content.pm.PackageInfo): String {
        val signingInfo = requireNotNull(packageInfo.signingInfo) { "Extension package has no signing identity" }
        require(!signingInfo.hasMultipleSigners()) { "Health fixture requires one current signer" }
        val current = signingInfo.apkContentsSigners.toList()
        require(current.size == 1) { "Health fixture requires one current signer" }
        return MessageDigest.getInstance("SHA-256")
            .digest(current.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private data class TestSourcePolicy(
        val packageName: String,
        val sourceId: String,
        val policy: SourceNetworkPolicy,
    )

    private companion object {
        fun policy(
            vararg hosts: String,
            namedCapabilities: Set<String> = setOf(
                NamedHostCapabilities.RESOURCE_READ,
                NamedHostCapabilities.EXTERNAL_LINK,
            ),
        ) = SourceNetworkPolicy(
            exactHosts = hosts.toSet(),
            operations = mapOf(
                NetworkOperations.SOURCE_READ to NetworkOperationPolicy(
                    name = NetworkOperations.SOURCE_READ,
                    methods = setOf("GET", "HEAD"),
                    pathPrefixes = setOf("/"),
                    credentialed = true,
                ),
            ),
            namedCapabilities = namedCapabilities,
        )

        val SOURCE_POLICIES = listOf(
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.eyny",
                "tw.kevinzhang.eyny",
                policy(
                    "eyny.com", "www.eyny.com",
                    namedCapabilities = setOf(
                        NamedHostCapabilities.RESOURCE_READ,
                        NamedHostCapabilities.EXTERNAL_LINK,
                        NamedHostCapabilities.EYNY_CHALLENGE_PROOF,
                    ),
                ),
            ),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.gamer", "tw.kevinzhang.newshub.extension.gamer", policy("forum.gamer.com.tw")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.hackernews", "tw.kevinzhang.newshub.extension.hackernews", policy("news.ycombinator.com")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.komica.twocat", policy("2cat.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.komica.sora", policy("komica1.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.akraft", policy("www.akraft.net")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.nagatoyuki", policy("eclair.nagatoyuki.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica", "tw.kevinzhang.wtako", policy("kemono.wtako.net")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.twocat", policy("2cat.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.sora", policy("komica1.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.komica2", "tw.kevinzhang.komica2.zawarudo", policy("majeur.zawarudo.org")),
            TestSourcePolicy("tw.kevinzhang.newshub.extension.mobile01", "tw.kevinzhang.mobile01", policy("www.mobile01.com")),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.ptt",
                "tw.kevinzhang.newshub.extension.ptt",
                policy(
                    "www.ptt.cc",
                    namedCapabilities = setOf(
                        NamedHostCapabilities.RESOURCE_READ,
                        NamedHostCapabilities.EXTERNAL_LINK,
                        NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS,
                    ),
                ),
            ),
        )

        private const val PRODUCTION_EXTENSION_SIGNER_SHA256 =
            "3df4717435423d5ba7adfed43a22a6e18bbeadc8d509d0bea94d82c7b0f2998d"

        // The published distribution currently maintains upgrade compatibility through one
        // production signer. Keep this as an explicit pin: the live fixture must never infer
        // trust from whichever APK happens to be installed on the test device.
        val SIGNER_PINS = SOURCE_POLICIES.associate { policy ->
            policy.packageName to PRODUCTION_EXTENSION_SIGNER_SHA256
        }
    }
}
