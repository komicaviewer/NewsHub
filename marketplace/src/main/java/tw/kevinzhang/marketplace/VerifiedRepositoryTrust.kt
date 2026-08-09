package tw.kevinzhang.marketplace

data class RepositorySigningPolicy(
    val packageName: String,
    val expectedVersionCode: Long,
    val targetLength: Long,
    val targetSha256: String,
    val lineageAnchorsSha256: Set<String>,
    val approvedCurrentSignersSha256: Set<String>,
    val sources: Map<String, RepositorySourceService>,
)

data class RepositorySourceService(
    val serviceClassName: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val protocol: Int,
    val policyHash: String,
)

data class VerifiedRepositoryTrustSnapshot(
    val rootVersion: Long,
    val targetsVersion: Long,
    val expiresAtEpochMillis: Long,
    val policies: List<RepositorySigningPolicy>,
)

fun interface VerifiedRepositoryTrustConsumer {
    fun install(snapshot: VerifiedRepositoryTrustSnapshot)
}
