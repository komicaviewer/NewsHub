package tw.kevinzhang.marketplace.data

data class ExtensionInfo(
    val id: String,
    val name: String,
    val version: Long,
    val versionName: String,
    val language: String,
    val iconUrl: String?,
    val apkUrl: String,
    val sha256: String? = null,
)

// index.json root
data class ExtensionIndex(val extensions: List<ExtensionInfo>)

enum class InstallState { NOT_INSTALLED, INSTALLED, UPDATE_AVAILABLE }
