package tw.kevinzhang.extension_loader

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.security.MessageDigest
import tw.kevinzhang.extension_api.ExtensionProtocol

class InstalledExtensionVerifierTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `accepts only the exact signed monolithic APK bytes and version`() {
        val apk = temporaryFolder.newFile("extension.apk").toPath()
        Files.write(apk, "trusted-apk".toByteArray())
        val policy = policy(Files.size(apk), sha256(Files.readAllBytes(apk)))

        verifyInstalledPackageArtifact(
            InstalledPackageArtifact(versionCode = 9, sourcePath = apk, splitSourcePaths = emptyList()),
            policy,
        )

        assertRejected(policy) { copy(versionCode = 8) }
        assertRejected(policy) { copy(splitSourcePaths = listOf("config.arm64_v8a.apk")) }
        assertRejected(policy.copy(targetLength = policy.targetLength + 1)) { this }
        assertRejected(policy.copy(targetSha256 = "f".repeat(64))) { this }
    }

    @Test fun `rejects symlink in place of installed base APK`() {
        val realApk = temporaryFolder.newFile("real.apk").toPath()
        Files.write(realApk, "trusted-apk".toByteArray())
        val symlink = temporaryFolder.root.toPath().resolve("linked.apk")
        Files.createSymbolicLink(symlink, realApk)
        val policy = policy(Files.size(realApk), sha256(Files.readAllBytes(realApk)))

        assertRejected(policy) {
            InstalledPackageArtifact(versionCode = 9, sourcePath = symlink, splitSourcePaths = emptyList())
        }
    }

    @Test fun `accepts only an exact older artifact explicitly signed by current targets`() {
        val oldApk = temporaryFolder.newFile("old-extension.apk").toPath()
        Files.write(oldApk, "old-trusted-apk".toByteArray())
        val current = temporaryFolder.newFile("current-extension.apk").toPath()
        Files.write(current, "current-trusted-apk".toByteArray())
        val accepted = AcceptedExtensionArtifact(
            versionCode = 8,
            length = Files.size(oldApk),
            sha256 = sha256(Files.readAllBytes(oldApk)),
        )
        val policy = policy(Files.size(current), sha256(Files.readAllBytes(current))).copy(
            acceptedArtifacts = listOf(accepted),
        )

        val result = verifyInstalledPackageArtifact(
            InstalledPackageArtifact(versionCode = 8, sourcePath = oldApk, splitSourcePaths = emptyList()),
            policy,
        )
        assertTrue(result == accepted)
        assertTrue(
            runCatching {
                verifyInstalledPackageArtifact(
                    InstalledPackageArtifact(versionCode = 7, sourcePath = oldApk, splitSourcePaths = emptyList()),
                    policy,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                verifyInstalledPackageArtifact(
                    InstalledPackageArtifact(versionCode = 8, sourcePath = current, splitSourcePaths = emptyList()),
                    policy,
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                policy.copy(acceptedArtifacts = listOf(accepted.copy(sha256 = "D".repeat(64))))
            }.isFailure,
        )
        // Hash reuse across two different, explicitly listed versions is not an implicit wildcard.
        policy.copy(
            acceptedArtifacts = listOf(
                accepted,
                accepted.copy(versionCode = 7),
            ),
        )
    }

    @Test fun `rejects every requested Android permission`() {
        val apk = temporaryFolder.newFile("permissioned-extension.apk").toPath()
        Files.write(apk, "trusted-apk".toByteArray())
        val policy = policy(Files.size(apk), sha256(Files.readAllBytes(apk)))
        val artifact = InstalledPackageArtifact(
            versionCode = 9,
            sourcePath = apk,
            splitSourcePaths = emptyList(),
            requestedPermissions = listOf("android.permission.INTERNET"),
        )

        try {
            verifyInstalledPackageArtifact(artifact, policy)
            throw AssertionError("Expected requested permission rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("must not request Android permissions"))
        }
    }

    @Test fun `installed Source service set must exactly match signed target`() {
        val descriptor = descriptor("example.source", "example.extension.ExampleService")
        val policy = policy(length = 4, hash = "c".repeat(64))
        verifyExpectedServiceSet(listOf(descriptor), policy)

        assertTrue(runCatching { verifyExpectedServiceSet(emptyList(), policy) }.isFailure)
        assertTrue(
            runCatching {
                verifyExpectedServiceSet(
                    listOf(descriptor, descriptor("attacker.source", "example.extension.HiddenService")),
                    policy,
                )
            }.isFailure,
        )
        assertTrue(runCatching { verifyExpectedServiceSet(listOf(descriptor, descriptor), policy) }.isFailure)
    }

    @Test fun `package replacement marker rejects every bind-time drift`() {
        val initial = InstalledPackageMarker(
            versionCode = 9,
            sourceDir = "/data/app/example/base.apk",
            splitNames = emptyList(),
            splitSourceDirs = emptyList(),
            lastUpdateTime = 1_800_000_000_000L,
            lineageAnchorSha256 = "a".repeat(64),
            currentSignerSha256 = "b".repeat(64),
        )
        verifyPackageUnchangedAfterBind(initial, initial)
        listOf(
            initial.copy(versionCode = 10),
            initial.copy(sourceDir = "/data/app/replaced/base.apk"),
            initial.copy(splitNames = listOf("config")),
            initial.copy(splitSourceDirs = listOf("/data/app/replaced/config.apk")),
            initial.copy(lastUpdateTime = initial.lastUpdateTime + 1),
            initial.copy(lineageAnchorSha256 = "c".repeat(64)),
            initial.copy(currentSignerSha256 = "d".repeat(64)),
        ).forEach { changed ->
            assertTrue(runCatching { verifyPackageUnchangedAfterBind(initial, changed) }.isFailure)
        }
    }

    private fun assertRejected(
        policy: ExtensionSigningPolicy,
        artifact: InstalledPackageArtifact.() -> InstalledPackageArtifact,
    ) {
        val base = InstalledPackageArtifact(
            versionCode = 9,
            sourcePath = temporaryFolder.root.toPath().resolve("extension.apk"),
            splitSourcePaths = emptyList(),
        )
        assertTrue(runCatching { verifyInstalledPackageArtifact(base.artifact(), policy) }.isFailure)
    }

    private fun policy(length: Long, hash: String) = ExtensionSigningPolicy(
        packageName = "example.extension",
        expectedVersionCode = 9,
        targetLength = length,
        targetSha256 = hash,
        lineageAnchorsSha256 = setOf("a".repeat(64)),
        approvedCurrentSignersSha256 = setOf("a".repeat(64)),
        sources = mapOf(
            "example.source" to ExpectedSourceService(
                serviceClassName = "example.extension.ExampleService",
                name = "Example",
                lang = "en",
                baseUrl = "https://example.com",
                protocol = ExtensionProtocol.VERSION,
                policyHash = "b".repeat(64),
            ),
        ),
    )

    private fun descriptor(sourceId: String, serviceClass: String) = ExtensionDescriptor(
        packageName = "example.extension",
        serviceClassName = serviceClass,
        processName = "example.extension:source",
        sourceId = sourceId,
        name = "Example",
        lang = "en",
        baseUrl = "https://example.com",
        protocol = ExtensionProtocol.VERSION,
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
