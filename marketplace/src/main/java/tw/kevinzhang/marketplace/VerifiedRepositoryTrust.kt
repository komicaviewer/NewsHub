package tw.kevinzhang.marketplace

import tw.kevinzhang.extension_api.SourceNetworkPolicy

data class RepositorySigningPolicy(
    val packageName: String,
    val expectedVersionCode: Long,
    val targetLength: Long,
    val targetSha256: String,
    val acceptedArtifacts: List<RepositoryAcceptedArtifact> = emptyList(),
    val lineageAnchorsSha256: Set<String>,
    val approvedCurrentSignersSha256: Set<String>,
    val sources: Map<String, RepositorySourceService>,
    val repositoryDomainId: String = RepositoryTrustDomains.OFFICIAL_ID,
)

data class RepositoryAcceptedArtifact(
    val versionCode: Long,
    val length: Long,
    val sha256: String,
)

data class RepositorySourceService(
    val serviceClassName: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val protocol: Int,
    val policyHash: String,
    val networkPolicy: SourceNetworkPolicy? = null,
)

data class VerifiedRepositoryTrustSnapshot(
    val rootVersion: Long,
    val targetsVersion: Long,
    val expiresAtEpochMillis: Long,
    val policies: List<RepositorySigningPolicy>,
    val repositoryDomainId: String = RepositoryTrustDomains.OFFICIAL_ID,
)

fun interface VerifiedRepositoryTrustConsumer {
    fun install(snapshot: VerifiedRepositoryTrustSnapshot)

    /**
     * Host integration overrides this to revoke capabilities and rescan installed APKs. Keeping a
     * default preserves source compatibility for non-host consumers and focused repository tests.
     */
    fun setDomainState(repositoryDomainId: String, state: RepositoryDomainState) = Unit
}
