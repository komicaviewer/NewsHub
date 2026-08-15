package tw.kevinzhang.extension_loader

import tw.kevinzhang.extension_api.Source

/**
 * Represents an extension APK that is currently installed on the device.
 * Analogous to mihon's Extension.Installed.
 */
data class InstalledExtension(
    /** The APK package name, e.g. "tw.kevinzhang.extension.gamer" */
    val pkgName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    /** Sources provided by this extension. */
    val sources: List<Source>,
    /** Repository, target, signer, and Source claims that authorized this exact installed APK. */
    val provenance: InstalledExtensionProvenance? = null,
    /** True if a newer version is available in a repo. */
    val hasUpdate: Boolean = false,
)

data class InstalledExtensionProvenance(
    val repositoryDomainId: String,
    val packageName: String,
    val targetSha256: String,
    val targetLength: Long,
    val lineageAnchorSha256: String,
    val currentSignerSha256: String,
    val sources: List<InstalledSourceProvenance>,
) {
    init {
        require(runCatching { java.util.UUID.fromString(repositoryDomainId).toString() == repositoryDomainId }.getOrDefault(false))
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(targetSha256.matches(Regex("[a-f0-9]{64}")) && targetLength > 0)
        require(lineageAnchorSha256.matches(Regex("[a-f0-9]{64}")))
        require(currentSignerSha256.matches(Regex("[a-f0-9]{64}")))
        require(sources.isNotEmpty() && sources.map(InstalledSourceProvenance::sourceId).distinct().size == sources.size)
    }
}

data class InstalledSourceProvenance(
    val sourceId: String,
    val policyHash: String,
) {
    init {
        require(sourceId.matches(Regex("[A-Za-z0-9._-]{1,160}")))
        require(policyHash.matches(Regex("[a-f0-9]{64}")))
    }
}
