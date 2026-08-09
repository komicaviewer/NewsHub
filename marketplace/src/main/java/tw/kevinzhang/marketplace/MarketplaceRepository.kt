package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File

interface MarketplaceRepository {
    /** Returns only metadata authenticated by the embedded-root trusted client. */
    suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata

    /** Returns only targets authenticated by the embedded-root trusted client. */
    suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo>

    fun getInstallState(info: ExtensionInfo): InstallState
    suspend fun downloadApk(info: ExtensionInfo): File
}
