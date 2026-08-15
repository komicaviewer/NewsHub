package tw.kevinzhang.marketplace.data

import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.marketplace.RepositoryTrustDomains

data class RepoMetadata(
    val name: String,
    val description: String,
    val baseUrl: String,
    val iconUrl: String? = null,
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: Long,
    val versionName: String,
    val language: String,
    val iconUrl: String?,
    val apkUrl: String,
    /** SHA-256 hex digest authenticated by threshold-signed targets metadata. */
    val sha256: String,
    val targetLength: Long,
    /** Stable first certificate in the approved Android signing lineage. */
    val lineageRootSha256: String,
    val signerPins: Set<String>,
    /** Sources bundled in this extension. */
    val sources: List<AvailableSource> = emptyList(),
    val repositoryDomainId: String = RepositoryTrustDomains.OFFICIAL_ID,
)

/** Metadata for a single Source inside an extension, as declared in index.min.json. */
data class AvailableSource(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val serviceClass: String,
    val protocol: Int,
    val policyHash: String,
    /** Full threshold-signed capability policy, present for verified TUF targets. */
    val networkPolicy: SourceNetworkPolicy? = null,
)

// index.min.json root
data class ExtensionIndex(val extensions: List<ExtensionInfo>)

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }

/** Fine-grained install progress, used for reactive UI updates. */
enum class InstallStep {
    IDLE,
    PENDING,
    DOWNLOADING,
    INSTALLING,
    INSTALLED,
    ERROR,
}

// Internal DTO matching new index.min.json flat-array format
internal data class RemoteExtensionDto(
    val pkg: String = "",
    val name: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val lang: String = "",
    val apkName: String = "",
    val iconName: String = "",
    val sha256: String = "",
    val sources: List<RemoteSourceDto> = emptyList(),
)

internal data class RemoteSourceDto(
    val id: String = "",
    val name: String = "",
    val lang: String = "",
    val baseUrl: String = "",
)
