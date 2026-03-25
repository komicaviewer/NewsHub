package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import java.io.File

interface MarketplaceRepository {
    suspend fun fetchIndex(): List<ExtensionInfo>
    fun getInstallState(info: ExtensionInfo): InstallState
    suspend fun downloadApk(apkUrl: String): File
}
