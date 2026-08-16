package tw.kevinzhang.extension_loader

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256

internal data class InstalledPackageArtifact(
    val versionCode: Long,
    val sourcePath: Path,
    val splitSourcePaths: List<String>,
    val requestedPermissions: List<String> = emptyList(),
)

internal data class InstalledPackageMarker(
    val versionCode: Long,
    val sourceDir: String,
    val splitNames: List<String>,
    val splitSourceDirs: List<String>,
    val lastUpdateTime: Long,
    val lineageAnchorSha256: String,
    val currentSignerSha256: String,
)

internal fun verifyPackageUnchangedAfterBind(
    initial: InstalledPackageMarker,
    current: InstalledPackageMarker,
) {
    require(current == initial) { "Extension package changed while binding" }
}

internal fun verifyInstalledPackageArtifact(
    artifact: InstalledPackageArtifact,
    policy: ExtensionSigningPolicy,
): AcceptedExtensionArtifact {
    val authorizedArtifact = if (artifact.versionCode == policy.expectedVersionCode) {
        AcceptedExtensionArtifact(policy.expectedVersionCode, policy.targetLength, policy.targetSha256)
    } else {
        requireNotNull(policy.acceptedArtifacts.singleOrNull { it.versionCode == artifact.versionCode }) {
            "Installed extension version is not accepted by signed targets"
        }
    }
    require(artifact.splitSourcePaths.isEmpty()) { "Split extension APKs are not supported" }
    require(artifact.requestedPermissions.isEmpty()) {
        "Extension package must not request Android permissions"
    }
    require(Files.isRegularFile(artifact.sourcePath, LinkOption.NOFOLLOW_LINKS)) {
        "Installed extension base APK is not a regular file"
    }
    require(Files.size(artifact.sourcePath) == authorizedArtifact.length) {
        "Installed extension APK length does not match signed target"
    }
    val installedSha256 = Files.newInputStream(artifact.sourcePath).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            require(totalBytes <= authorizedArtifact.length) {
                "Installed extension APK grew during verification"
            }
            digest.update(buffer, 0, read)
        }
        require(totalBytes == authorizedArtifact.length) {
            "Installed extension APK changed during verification"
        }
        digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
    require(MessageDigest.isEqual(installedSha256.toByteArray(), authorizedArtifact.sha256.toByteArray())) {
        "Installed extension APK hash does not match signed target"
    }
    return authorizedArtifact
}

internal fun verifyExpectedServiceSet(
    descriptors: List<ExtensionDescriptor>,
    policy: ExtensionSigningPolicy,
) {
    require(descriptors.isNotEmpty()) { "Extension package has no Source services" }
    require(descriptors.map(ExtensionDescriptor::sourceId).distinct().size == descriptors.size) {
        "Extension package has duplicate Source ids"
    }
    require(descriptors.map(ExtensionDescriptor::serviceClassName).distinct().size == descriptors.size) {
        "Extension package has duplicate Service classes"
    }
    require(descriptors.mapTo(linkedSetOf(), ExtensionDescriptor::sourceId) == policy.sources.keys) {
        "Installed Source service set does not match signed target"
    }
}

internal fun verifyExpectedNetworkPolicyHash(
    expectedPolicyHash: String,
    networkPolicy: SourceNetworkPolicy,
) {
    require(expectedPolicyHash == networkPolicy.sha256()) {
        "Signed Source policy hash does not match Host policy"
    }
}

internal fun verifyServiceDescriptor(
    descriptor: ExtensionDescriptor,
    expected: ExpectedSourceService,
) {
    require(descriptor.serviceClassName == expected.serviceClassName) { "Source Service class mismatch" }
    require(descriptor.name == expected.name) { "Source display name mismatch" }
    require(descriptor.lang == expected.lang) { "Source language mismatch" }
    require(descriptor.baseUrl == expected.baseUrl) { "Source base URL mismatch" }
    require(descriptor.protocol == expected.protocol) { "Source protocol mismatch" }
}
