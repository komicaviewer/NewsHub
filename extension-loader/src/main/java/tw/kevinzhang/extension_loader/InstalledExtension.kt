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
    /** True if a newer version is available in a repo. */
    val hasUpdate: Boolean = false,
)
