package tw.kevinzhang.extension_loader

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

data class ExpectedSourceService(
    val serviceClassName: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val protocol: Int,
    val policyHash: String,
) {
    init {
        require(serviceClassName.matches(Regex("[A-Za-z_][A-Za-z0-9_.]{2,511}")))
        require(name.isNotBlank() && name.length <= 512)
        require(lang.matches(Regex("[A-Za-z0-9-]{1,35}")))
        require(baseUrl.length <= 512 && runCatching {
            java.net.URI(baseUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() }
        }.getOrDefault(false))
        require(protocol > 0)
        require(isSha256(policyHash))
    }
}

data class ExtensionSigningPolicy(
    val packageName: String,
    val expectedVersionCode: Long,
    val targetLength: Long,
    val targetSha256: String,
    val lineageAnchorsSha256: Set<String>,
    val approvedCurrentSignersSha256: Set<String>,
    val sources: Map<String, ExpectedSourceService>,
) {
    init {
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(expectedVersionCode > 0)
        require(targetLength in 1..MAX_EXTENSION_APK_BYTES)
        require(lineageAnchorsSha256.isNotEmpty() && approvedCurrentSignersSha256.isNotEmpty())
        require((lineageAnchorsSha256 + approvedCurrentSignersSha256 + targetSha256).all(::isSha256))
        require(sources.isNotEmpty() && sources.keys.all { it.matches(Regex("[A-Za-z0-9._-]{1,160}")) })
    }
}

data class VerifiedExtensionTrustSnapshot(
    val rootVersion: Long,
    val targetsVersion: Long,
    val expiresAtEpochMillis: Long,
    val policies: List<ExtensionSigningPolicy>,
) {
    init {
        require(rootVersion > 0 && targetsVersion > 0)
        require(expiresAtEpochMillis > 0)
        require(policies.map(ExtensionSigningPolicy::packageName).distinct().size == policies.size)
    }
}

/**
 * In-memory authorization view populated only after the repository layer verifies threshold
 * signatures, expiry, rollback counters, package ownership, and target hashes. Empty/expired
 * state deliberately quarantines every extension; no compiled development certificate is trusted.
 */
@Singleton
class ExtensionTrustPolicyProvider @Inject constructor() {
    private val snapshot = AtomicReference<VerifiedExtensionTrustSnapshot?>()

    @Synchronized
    fun installVerifiedSnapshot(verifiedSnapshot: VerifiedExtensionTrustSnapshot) {
        val normalizedSnapshot = verifiedSnapshot.normalized()
        require(normalizedSnapshot.expiresAtEpochMillis > System.currentTimeMillis()) {
            "Cannot install expired extension trust metadata"
        }
        snapshot.get()?.let { current ->
            require(normalizedSnapshot.rootVersion >= current.rootVersion) { "Extension root rollback" }
            require(normalizedSnapshot.targetsVersion >= current.targetsVersion) { "Extension targets rollback" }
            if (normalizedSnapshot.targetsVersion == current.targetsVersion) {
                require(normalizedSnapshot.copy(rootVersion = current.rootVersion) == current) {
                    "Unchanged targets version replaced extension trust policy"
                }
            }
        }
        snapshot.set(normalizedSnapshot)
    }

    fun clear() {
        snapshot.set(null)
    }

    internal fun policyFor(
        packageName: String,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
    ): ExtensionSigningPolicy? {
        val current = snapshot.get()?.takeIf { it.expiresAtEpochMillis > now } ?: return null
        return current.policies.singleOrNull { policy ->
            policy.packageName == packageName && sourceId in policy.sources
        }
    }

    internal fun policyForPackage(
        packageName: String,
        now: Long = System.currentTimeMillis(),
    ): ExtensionSigningPolicy? {
        val current = snapshot.get()?.takeIf { it.expiresAtEpochMillis > now } ?: return null
        return current.policies.singleOrNull { it.packageName == packageName }
    }

    private fun VerifiedExtensionTrustSnapshot.normalized() = copy(
        policies = policies.map { policy ->
            policy.copy(
                lineageAnchorsSha256 = policy.lineageAnchorsSha256.mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) },
                approvedCurrentSignersSha256 = policy.approvedCurrentSignersSha256
                    .mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) },
                targetSha256 = policy.targetSha256.lowercase(Locale.ROOT),
                sources = policy.sources.mapValues { (_, source) ->
                    source.copy(policyHash = source.policyHash.lowercase(Locale.ROOT))
                },
            )
        },
    )
}

private fun isSha256(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{64}"))

private const val MAX_EXTENSION_APK_BYTES = 64L * 1024 * 1024
