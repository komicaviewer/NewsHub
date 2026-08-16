package tw.kevinzhang.newshub.extension

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.NetworkOperationPolicy
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.NetworkRequestRule
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.WebLoginUserAgentProvider
import tw.kevinzhang.extension_api.sha256
import tw.kevinzhang.extension_loader.AcceptedExtensionArtifact
import tw.kevinzhang.extension_loader.ExpectedSourceService
import tw.kevinzhang.extension_loader.ExtensionSigningPolicy
import tw.kevinzhang.extension_loader.VerifiedExtensionTrustSnapshot
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ExtensionIsolationE2ETest {
    @Test
    fun liveHealthFixtureMatchesPublishedV2NetworkPolicies() {
        assertTrue(SOURCE_POLICIES.all { it.policy.policyVersion == 2 })
        assertTrue(SOURCE_POLICIES.all { it.policy.operations.isEmpty() })
        assertEquals(
            PUBLISHED_POLICY_HASHES,
            SOURCE_POLICIES.associate { it.sourceId to it.policy.sha256() },
        )

        val gamer = SOURCE_POLICIES.single {
            it.sourceId == "tw.kevinzhang.newshub.extension.gamer"
        }.policy
        assertEquals(setOf("api.gamer.com.tw", "forum.gamer.com.tw"), gamer.exactHosts)

        val hackerNews = SOURCE_POLICIES.single {
            it.sourceId == "tw.kevinzhang.newshub.extension.hackernews"
        }.policy
        assertEquals(setOf("hacker-news.firebaseio.com"), hackerNews.exactHosts)

        val komica2Sora = SOURCE_POLICIES.single {
            it.sourceId == "tw.kevinzhang.komica2.sora"
        }.policy
        assertEquals(setOf("2cat.org", "2cat.uk"), komica2Sora.exactHosts)
        assertTrue("komica1.org" in komica2Sora.resourceExactHosts)
    }

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
        val allowDisposableTestSigner =
            InstrumentationRegistry.getArguments().getString(ALLOW_DISPOSABLE_TEST_SIGNER) == "true"
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
        withTimeout(15_000) {
            while (entryPoint.manager().quarantinedExtensions.value.size < SOURCE_POLICIES.size) delay(50)
        }
        assertTrue(entryPoint.manager().installedExtensions.value.isEmpty())
        assertEquals(SOURCE_POLICIES.size, entryPoint.manager().quarantinedExtensions.value.size)

        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(
                context,
                targetsVersion = 3,
                validPins = true,
                validContent = true,
                // Local disposable emulators build protocol-v2 APKs with the Android debug key.
                // Production/default runs keep the reviewed signer pins below.
                pinInstalledSigner = allowDisposableTestSigner,
            ),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(20_000) {
            while (entryPoint.loader().sourcesFlow.value.size != SOURCE_POLICIES.size) delay(50)
        }

        val installed = entryPoint.manager().installedExtensions.value
        assertEquals(7, installed.size)
        assertEquals(SOURCE_POLICIES.size, installed.sumOf { it.sources.size })
        assertTrue(entryPoint.manager().quarantinedExtensions.value.isEmpty())

        val loadedSources = entryPoint.loader().sourcesFlow.value
        assertTrue(loadedSources.none { it.supportsCommentPagination })
        assertTrue(loadedSources.any { it.alwaysUseRawImage })
        assertTrue(loadedSources.any { !it.alwaysUseRawImage })
        val authenticated = loadedSources.filterIsInstance<AuthenticatedSource>()
        assertEquals(
            setOf(
                "tw.kevinzhang.eyny",
                "tw.kevinzhang.newshub.extension.gamer",
                "tw.kevinzhang.newshub.extension.ptt",
            ),
            authenticated.mapTo(linkedSetOf()) { it.id },
        )
        assertTrue(
            authenticated.joinToString { "${it.id}:needsLogin=${it.needsLogin}" },
            authenticated.none { it.needsLogin },
        )
        authenticated.forEach { source ->
            val auth = source.authSpec as AuthSpec.WebCookie
            assertTrue(auth.allowedHosts.isNotEmpty())
            assertTrue(auth.cookieOrigins.isNotEmpty())
            assertTrue(auth.cookieDomains.isNotEmpty())
        }
        val eyny = authenticated.single { it.id == "tw.kevinzhang.eyny" }
        assertTrue((eyny as WebLoginUserAgentProvider).webLoginUserAgent.isNotBlank())

        // Sora categories are in-memory constants. A successful response proves the explicit
        // Binder/PFD protocol executed extension code without relying on external network state.
        val sora = entryPoint.loader().getSource("tw.kevinzhang.komica.sora")
            ?: error("Trusted Sora Source was not loaded")
        val categories = withTimeout(10_000) { sora.getBoardCategories() }
        assertTrue(categories.isNotEmpty())
        assertEquals("tw.kevinzhang.newshub.extension.komica", sora.sourceIdentity?.packageName)

        // A newly published target may explicitly keep the exact installed artifact usable while
        // Android waits for user-confirmed installation of the update.
        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(
                context,
                targetsVersion = 4,
                validPins = true,
                validContent = true,
                pinInstalledSigner = allowDisposableTestSigner,
                expectedVersionDelta = 1,
                acceptInstalledArtifact = true,
            ),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(20_000) {
            while (entryPoint.loader().sourcesFlow.value.size != SOURCE_POLICIES.size) delay(50)
        }
        assertTrue(entryPoint.manager().quarantinedExtensions.value.isEmpty())

        // Omitting that exact compatibility triple in the next signed targets version is an
        // immediate revocation, not an implicit grace period from locally cached metadata.
        entryPoint.trustProvider().installVerifiedSnapshot(
            snapshot(
                context,
                targetsVersion = 5,
                validPins = true,
                validContent = true,
                pinInstalledSigner = allowDisposableTestSigner,
                expectedVersionDelta = 1,
            ),
        )
        entryPoint.manager().refreshAllExtensionsAndAwait()
        withTimeout(15_000) {
            while (entryPoint.manager().quarantinedExtensions.value.size < SOURCE_POLICIES.size) delay(50)
        }
        assertTrue(entryPoint.manager().installedExtensions.value.isEmpty())
        assertEquals(SOURCE_POLICIES.size, entryPoint.manager().quarantinedExtensions.value.size)
    }

    @Suppress("DEPRECATION")
    internal fun snapshot(
        context: Context,
        targetsVersion: Long,
        validPins: Boolean,
        validContent: Boolean,
        sourceIds: Set<String> = SOURCE_POLICIES.mapTo(linkedSetOf()) { it.sourceId },
        pinInstalledSigner: Boolean = false,
        expectedVersionDelta: Long = 0,
        acceptInstalledArtifact: Boolean = false,
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
            val installedVersion = PackageInfoCompat.getLongVersionCode(packageInfo)
            val installedSha256 = baseApk.sha256()
            val pin = if (validPins) {
                if (pinInstalledSigner) installedSigner else SIGNER_PINS.getValue(packageName)
            } else {
                "0".repeat(64)
            }
            ExtensionSigningPolicy(
                packageName = packageName,
                expectedVersionCode = installedVersion + expectedVersionDelta,
                targetLength = baseApk.length(),
                targetSha256 = when {
                    !validContent -> "0".repeat(64)
                    expectedVersionDelta == 0L -> installedSha256
                    else -> "1".repeat(64)
                },
                acceptedArtifacts = if (acceptInstalledArtifact) {
                    listOf(AcceptedExtensionArtifact(installedVersion, baseApk.length(), installedSha256))
                } else {
                    emptyList()
                },
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
                        networkPolicy = source.policy,
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
        fun requestRule(
            vararg hosts: String,
            pathPrefixes: Set<String> = setOf("/"),
            credentialed: Boolean = false,
        ) = NetworkRequestRule(
            exactHosts = hosts.toSet(),
            operation = NetworkOperationPolicy(
                name = NetworkOperations.SOURCE_READ,
                methods = setOf("GET"),
                pathPrefixes = pathPrefixes,
                credentialed = credentialed,
            ),
        )

        fun policy(
            requestRules: List<NetworkRequestRule>,
            resourceHosts: Set<String>,
            externalHosts: Set<String>,
            authHosts: Set<String> = emptySet(),
            namedCapabilities: Set<String> = setOf(
                NamedHostCapabilities.RESOURCE_READ,
                NamedHostCapabilities.EXTERNAL_LINK,
            ),
        ) = SourceNetworkPolicy(
            exactHosts = requestRules.flatMapTo(linkedSetOf(), NetworkRequestRule::exactHosts),
            // Version 2 represents request authorization exclusively through requestRules.
            // Keeping the legacy v1 operations map populated makes the loader reject the
            // otherwise correctly hashed policy before any extension can bind.
            operations = emptyMap(),
            namedCapabilities = namedCapabilities,
            policyVersion = 2,
            resourceExactHosts = resourceHosts,
            externalExactHosts = externalHosts,
            authExactHosts = authHosts,
            requestRules = requestRules,
        )

        fun hosts(vararg hosts: String): Set<String> = hosts.toSet()

        val SOURCE_POLICIES = listOf(
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.eyny",
                "tw.kevinzhang.eyny",
                policy(
                    requestRules = listOf(
                        requestRule("eyny.com", "www.eyny.com", "www52.eyny.com", "www53.eyny.com", credentialed = true),
                    ),
                    resourceHosts = hosts("eyny.com", "www.eyny.com", "www52.eyny.com", "www53.eyny.com"),
                    externalHosts = hosts("eyny.com", "www.eyny.com", "www52.eyny.com", "www53.eyny.com"),
                    authHosts = hosts("eyny.com", "www.eyny.com", "www52.eyny.com", "www53.eyny.com"),
                    namedCapabilities = setOf(
                        NamedHostCapabilities.RESOURCE_READ,
                        NamedHostCapabilities.EXTERNAL_LINK,
                        NamedHostCapabilities.EYNY_CHALLENGE_PROOF,
                    ),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.gamer",
                "tw.kevinzhang.newshub.extension.gamer",
                policy(
                    requestRules = listOf(
                        requestRule(
                            "api.gamer.com.tw",
                            pathPrefixes = setOf("/community/v1/", "/mobile_app/forum/v3/"),
                        ),
                        requestRule(
                            "forum.gamer.com.tw",
                            pathPrefixes = setOf("/B.php", "/C.php", "/ajax/"),
                            credentialed = true,
                        ),
                    ),
                    resourceHosts = hosts("i2.bahamut.com.tw"),
                    externalHosts = hosts("forum.gamer.com.tw"),
                    authHosts = hosts("forum.gamer.com.tw", "user.gamer.com.tw", "www.gamer.com.tw"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.hackernews",
                "tw.kevinzhang.newshub.extension.hackernews",
                policy(
                    requestRules = listOf(
                        requestRule("hacker-news.firebaseio.com", pathPrefixes = setOf("/v0/")),
                    ),
                    resourceHosts = hosts("avatars.githubusercontent.com"),
                    externalHosts = hosts("news.ycombinator.com"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica",
                "tw.kevinzhang.komica.twocat",
                policy(
                    requestRules = listOf(
                        requestRule("2cat.org", "cat.2nyan.org", "eclair.nagatoyuki.org", "www.gomiga.org"),
                    ),
                    resourceHosts = hosts(
                        "2cat.org", "2cat.uk", "cat.2nyan.org", "eclair.nagatoyuki.org", "www.gomiga.org",
                    ),
                    externalHosts = hosts(
                        "2cat.org", "cat.2nyan.org", "eclair.nagatoyuki.org", "www.gomiga.org",
                    ),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica",
                "tw.kevinzhang.komica.sora",
                policy(
                    requestRules = listOf(requestRule(
                        "fenrisulfr.org", "gaia.komica1.org", "gita.komica1.org", "iris.komica1.org",
                        "komica.dbfoxtw.me", "msgirls.boguspix.com", "pixmicat.alica.idv.tw",
                        "sister.boguspix.com", "storysol.boguspix.com", "travel.voidfactory.com",
                        "www.karlsland.net",
                    )),
                    resourceHosts = hosts(
                        "fenrisulfr.org", "gaia.komica1.org", "gita.komica1.org", "iris.komica1.org",
                        "komica.dbfoxtw.me", "komica1.org", "msgirls.boguspix.com", "pixmicat.alica.idv.tw",
                        "sister.boguspix.com", "storysol.boguspix.com", "travel.voidfactory.com",
                        "www.karlsland.net",
                    ),
                    externalHosts = hosts(
                        "fenrisulfr.org", "gaia.komica1.org", "gita.komica1.org", "iris.komica1.org",
                        "komica.dbfoxtw.me", "msgirls.boguspix.com", "pixmicat.alica.idv.tw",
                        "sister.boguspix.com", "storysol.boguspix.com", "travel.voidfactory.com",
                        "www.karlsland.net",
                    ),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica",
                "tw.kevinzhang.akraft",
                policy(
                    requestRules = listOf(requestRule("www.akraft.net", pathPrefixes = setOf("/service/"))),
                    resourceHosts = hosts("www.akraft.net"),
                    externalHosts = hosts("www.akraft.net"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica",
                "tw.kevinzhang.nagatoyuki",
                policy(
                    requestRules = listOf(requestRule("eclair.nagatoyuki.org", "selene.zawarudo.org", "www.gomiga.org")),
                    resourceHosts = hosts("eclair.nagatoyuki.org", "selene.zawarudo.org", "www.gomiga.org"),
                    externalHosts = hosts("eclair.nagatoyuki.org", "selene.zawarudo.org", "www.gomiga.org"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica",
                "tw.kevinzhang.wtako",
                policy(
                    requestRules = listOf(requestRule("kemono.wtako.net", "rthost.win", "www.karlsland.net")),
                    resourceHosts = hosts("kemono.wtako.net", "rthost.win", "www.karlsland.net"),
                    externalHosts = hosts("kemono.wtako.net", "rthost.win", "www.karlsland.net"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica2",
                "tw.kevinzhang.komica2.twocat",
                policy(
                    requestRules = listOf(requestRule("2cat.org", pathPrefixes = setOf("/touhoux/"))),
                    resourceHosts = hosts("2cat.org", "2cat.uk"),
                    externalHosts = hosts("2cat.org"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica2",
                "tw.kevinzhang.komica2.sora",
                policy(
                    requestRules = listOf(requestRule("2cat.org", "2cat.uk")),
                    resourceHosts = hosts("2cat.org", "2cat.uk", "komica1.org"),
                    externalHosts = hosts("2cat.org", "2cat.uk"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.komica2",
                "tw.kevinzhang.komica2.zawarudo",
                policy(
                    requestRules = listOf(
                        requestRule(
                            "majeur.zawarudo.org",
                            pathPrefixes = setOf("/demande", "/guro", "/hgame"),
                        ),
                    ),
                    resourceHosts = hosts("majeur.zawarudo.org"),
                    externalHosts = hosts("majeur.zawarudo.org"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.mobile01",
                "tw.kevinzhang.mobile01",
                policy(
                    requestRules = listOf(
                        requestRule(
                            "www.mobile01.com",
                            pathPrefixes = setOf("/topicdetail.php", "/topiclist.php"),
                        ),
                    ),
                    resourceHosts = hosts("attach2.mobile01.com", "www.mobile01.com"),
                    externalHosts = hosts("www.mobile01.com"),
                ),
            ),
            TestSourcePolicy(
                "tw.kevinzhang.newshub.extension.ptt",
                "tw.kevinzhang.newshub.extension.ptt",
                policy(
                    requestRules = listOf(
                        requestRule("www.ptt.cc", pathPrefixes = setOf("/bbs/"), credentialed = true),
                    ),
                    resourceHosts = hosts("i.imgur.com", "www.ptt.cc"),
                    externalHosts = hosts("www.ptt.cc"),
                    authHosts = hosts("www.ptt.cc"),
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
        private const val ALLOW_DISPOSABLE_TEST_SIGNER = "newshub.allowDisposableTestSigner"

        // The published distribution currently maintains upgrade compatibility through one
        // production signer. Keep this as an explicit pin: the live fixture must never infer
        // trust from whichever APK happens to be installed on the test device.
        val SIGNER_PINS = SOURCE_POLICIES.associate { policy ->
            policy.packageName to PRODUCTION_EXTENSION_SIGNER_SHA256
        }

        val PUBLISHED_POLICY_HASHES = mapOf(
            "tw.kevinzhang.eyny" to "9cbfa85fd151858f5443d64b2a9d2762879d30cd54ebf4bbd3d775ac26f4839c",
            "tw.kevinzhang.newshub.extension.gamer" to "d83562d39c756463f9e5d1ed8028cfde5ba53abb821d5f3c2e45ec71bcefc5dc",
            "tw.kevinzhang.newshub.extension.hackernews" to "87ecb81f9a175c91143a1400997454dbe552f83c437154c5cc89558209654c24",
            "tw.kevinzhang.komica.twocat" to "950e16defa0919fd7aa9eca0ac7929c12a28734bb3b36d6676205bdfe8bcf762",
            "tw.kevinzhang.komica.sora" to "bb1286c07671815139a69081e1356b865e1236e789f6b62511e6a1d9b568eb7f",
            "tw.kevinzhang.akraft" to "2d2d298c9c47ec1d631c73e40010a4f3582059577d2bc805b3c536a483d7433a",
            "tw.kevinzhang.nagatoyuki" to "eef264703bf2d8e8daa00b1882b71f6c82a2b2bd3d7b7e596fbcb44a5cadfc14",
            "tw.kevinzhang.wtako" to "92741d5558334282df9cc240010ea3ef4a141ced4a5a135311802241f13b2c00",
            "tw.kevinzhang.komica2.twocat" to "692ca4fc6068b2aaec7375ab7e819d51c7a218f92402b77568ea9fd1ffd317a5",
            "tw.kevinzhang.komica2.sora" to "2e9f6788904a5f7546292b947af18d65569e2b4152c2a1b0f4aa2952e71da70f",
            "tw.kevinzhang.komica2.zawarudo" to "585d25644328939cbbed7183690c09749500806832831d34c82632527b3880f6",
            "tw.kevinzhang.mobile01" to "1518d8988d341cfdae51de7ca17535726d840f94cb52c3ba09d80d164804eee7",
            "tw.kevinzhang.newshub.extension.ptt" to "974635280b574bbf7ba5c4e48ce6eb7fa4ffc8f9b59dde456322b37037ef3edc",
        )
    }
}
