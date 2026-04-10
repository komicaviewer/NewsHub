package tw.kevinzhang.marketplace.data

data class RepoMetadata(
    val name: String,
    val description: String,
    val baseUrl: String,
)

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: Long,
    val versionName: String,
    val language: String,
    val iconUrl: String?,
    val apkUrl: String,
    /** SHA-256 hex digest of the APK. When present, verified after download. Null for legacy index entries (integrity not enforced). */
    val sha256: String? = null,
    /** Sources bundled in this extension. */
    val sources: List<AvailableSource> = emptyList(),
)

/** Metadata for a single Source inside an extension, as declared in index.json. */
data class AvailableSource(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
)

// index.json root
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

// Internal DTO matching new index.json flat-array format
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
