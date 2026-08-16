package tw.kevinzhang.marketplace

import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File

interface MarketplaceRepository {
    val officialRepositoryDomain: RepositoryTrustDomain

    /** Download and self-verify only root.json. This does not persist trust. */
    suspend fun inspectRepositoryRoot(repoUrl: String): RepositoryRootPreview

    /** Explicit TOFU confirmation. A fresh UUID is generated only inside this operation. */
    suspend fun confirmRepositoryRoot(confirmationToken: String): RepositoryTrustDomain

    /** Forget a process-local preview. No domain or metadata has been written at this point. */
    fun cancelRepositoryRootInspection(confirmationToken: String)

    /** Restore persisted domain descriptors when the app starts. */
    fun registerRepositoryDomains(domains: Collection<RepositoryTrustDomain>)

    /** Apply a user decision immediately to the trust consumer. Revocation is irreversible. */
    fun setRepositoryDomainState(domain: RepositoryTrustDomain)

    /** Returns only metadata authenticated by the embedded-root trusted client. */
    suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata

    /** Returns only targets authenticated by the embedded-root trusted client. */
    suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo>

    fun getInstallState(info: ExtensionInfo): InstallState
    suspend fun downloadApk(info: ExtensionInfo): File
}
