package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState

interface MarketplaceRepository {
    suspend fun fetchIndex(): List<ExtensionInfo>
    fun getInstallState(info: ExtensionInfo): InstallState
}
