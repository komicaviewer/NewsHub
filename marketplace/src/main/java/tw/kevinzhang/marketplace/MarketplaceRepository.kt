package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File

interface MarketplaceRepository {
    /** Fetches repo.json metadata from a GitHub extension repo URL. */
    suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata

    /** Fetches index.min.json and returns all extensions from a GitHub extension repo URL. */
    suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo>

    fun getInstallState(info: ExtensionInfo): InstallState
    suspend fun downloadApk(apkUrl: String, expectedSha256: String?): File
}
