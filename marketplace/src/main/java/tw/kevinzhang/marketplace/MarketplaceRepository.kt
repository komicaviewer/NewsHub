package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File

interface MarketplaceRepository {
    /** Fails closed until the embedded-root trusted repository client is enabled. */
    suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata

    /** Fails closed until the embedded-root trusted repository client is enabled. */
    suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo>

    fun getInstallState(info: ExtensionInfo): InstallState
    suspend fun downloadApk(apkUrl: String, expectedSha256: String?): File
}
