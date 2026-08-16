package tw.kevinzhang.extension_loader

import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.sha256

/** Stable UUID assigned to the repository bundled with NewsHub during migration. */
const val BUILTIN_REPOSITORY_DOMAIN_ID = "00000000-0000-0000-0000-000000000001"

enum class RepositoryTrustDomainState {
    ACTIVE,
    EXPIRED,
    SUSPENDED,
    REVOKED,
}

data class ExpectedSourceService(
    val serviceClassName: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val protocol: Int,
    val policyHash: String,
    val repositoryDomainId: String = BUILTIN_REPOSITORY_DOMAIN_ID,
    /** Full policy covered by the verified targets metadata. A missing policy fails closed. */
    val networkPolicy: SourceNetworkPolicy? = null,
) {
    init {
        requireValidDomainId(repositoryDomainId)
        require(serviceClassName.matches(Regex("[A-Za-z_][A-Za-z0-9_.]{2,511}")))
        require(name.isNotBlank() && name.length <= 512)
        require(lang.matches(Regex("[A-Za-z0-9-]{1,35}")))
        val baseUri = requireNotNull(runCatching { URI(baseUrl) }.getOrNull()) { "Invalid Source base URL" }
        require(baseUrl.length <= 512 && baseUri.scheme == "https" && !baseUri.host.isNullOrBlank())
        require(protocol > 0)
        require(isSha256(policyHash))
        networkPolicy?.let { policy ->
            validateSignedNetworkPolicy(policy)
            require(baseUri.host.lowercase(Locale.ROOT) in policy.allExactHosts) {
                "Signed policy must authorize the Source base host"
            }
            require(policy.sha256().equals(policyHash, ignoreCase = true)) {
                "Signed Source policy hash does not match signed full policy"
            }
        }
    }
}

data class ExtensionSigningPolicy(
    val packageName: String,
    val expectedVersionCode: Long,
    val targetLength: Long,
    val targetSha256: String,
    /** Older exact APK triples explicitly covered by the current verified targets metadata. */
    val acceptedArtifacts: List<AcceptedExtensionArtifact> = emptyList(),
    val lineageAnchorsSha256: Set<String>,
    val approvedCurrentSignersSha256: Set<String>,
    val sources: Map<String, ExpectedSourceService>,
    val repositoryDomainId: String = BUILTIN_REPOSITORY_DOMAIN_ID,
) {
    init {
        requireValidDomainId(repositoryDomainId)
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        require(expectedVersionCode > 0)
        require(targetLength in 1..MAX_EXTENSION_APK_BYTES)
        require(lineageAnchorsSha256.isNotEmpty() && approvedCurrentSignersSha256.isNotEmpty())
        require((lineageAnchorsSha256 + approvedCurrentSignersSha256 + targetSha256).all(::isSha256))
        require(acceptedArtifacts.size <= MAX_ACCEPTED_ARTIFACTS)
        require(acceptedArtifacts.all { artifact ->
            artifact.versionCode in 1 until expectedVersionCode &&
                artifact.length in 1..MAX_EXTENSION_APK_BYTES && isSha256(artifact.sha256) &&
                artifact.sha256 == artifact.sha256.lowercase(Locale.ROOT)
        })
        require(acceptedArtifacts.map(AcceptedExtensionArtifact::versionCode).distinct().size == acceptedArtifacts.size)
        require(acceptedArtifacts.distinct().size == acceptedArtifacts.size)
        require(sources.isNotEmpty() && sources.keys.all { it.matches(Regex("[A-Za-z0-9._-]{1,160}")) })
        require(sources.values.all { it.repositoryDomainId == repositoryDomainId }) {
            "Source trust domain does not match package trust domain"
        }
    }
}

data class AcceptedExtensionArtifact(
    val versionCode: Long,
    val length: Long,
    val sha256: String,
)

data class VerifiedExtensionTrustSnapshot(
    val rootVersion: Long,
    val targetsVersion: Long,
    val expiresAtEpochMillis: Long,
    val policies: List<ExtensionSigningPolicy>,
    val repositoryDomainId: String = BUILTIN_REPOSITORY_DOMAIN_ID,
    val state: RepositoryTrustDomainState = RepositoryTrustDomainState.ACTIVE,
) {
    init {
        requireValidDomainId(repositoryDomainId)
        require(rootVersion > 0 && targetsVersion > 0)
        require(expiresAtEpochMillis > 0)
        require(state != RepositoryTrustDomainState.EXPIRED) { "Expiry is derived from verified metadata" }
        require(policies.map(ExtensionSigningPolicy::packageName).distinct().size == policies.size)
        require(policies.all { it.repositoryDomainId == repositoryDomainId }) {
            "Package trust domain does not match snapshot trust domain"
        }
    }

    fun effectiveState(now: Long = System.currentTimeMillis()): RepositoryTrustDomainState =
        if (state == RepositoryTrustDomainState.ACTIVE && expiresAtEpochMillis <= now) {
            RepositoryTrustDomainState.EXPIRED
        } else {
            state
        }
}

/**
 * Multi-domain authorization view populated only after each repository independently verifies its
 * TUF root and targets chain. Package and Source ownership conflicts fail closed unless the caller
 * records an explicit domain choice.
 */
@Singleton
class ExtensionTrustPolicyProvider @Inject constructor() {
    private val state = AtomicReference(ProviderState())
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    @Synchronized
    fun installVerifiedSnapshot(verifiedSnapshot: VerifiedExtensionTrustSnapshot) {
        var normalizedSnapshot = verifiedSnapshot.normalized()
        require(normalizedSnapshot.expiresAtEpochMillis > System.currentTimeMillis()) {
            "Cannot install expired extension trust metadata"
        }
        require(normalizedSnapshot.state != RepositoryTrustDomainState.REVOKED) {
            "Cannot install a revoked extension trust domain"
        }
        val currentState = state.get()
        currentState.snapshots[normalizedSnapshot.repositoryDomainId]?.let { current ->
            require(current.state != RepositoryTrustDomainState.REVOKED) {
                "Revoked trust domain must be onboarded with a new id"
            }
            // Repository refreshes update verified metadata, never a user's suspension decision.
            normalizedSnapshot = normalizedSnapshot.copy(state = current.state)
            require(normalizedSnapshot.rootVersion >= current.rootVersion) { "Extension root rollback" }
            require(normalizedSnapshot.targetsVersion >= current.targetsVersion) { "Extension targets rollback" }
            if (normalizedSnapshot.targetsVersion == current.targetsVersion) {
                require(normalizedSnapshot.copy(rootVersion = current.rootVersion) == current) {
                    "Unchanged targets version replaced extension trust policy"
                }
            }
        }
        state.set(
            currentState.copy(
                snapshots = currentState.snapshots +
                    (normalizedSnapshot.repositoryDomainId to normalizedSnapshot),
            ),
        )
        notifyChanged()
    }

    @Synchronized
    fun setDomainState(repositoryDomainId: String, newState: RepositoryTrustDomainState) {
        requireValidDomainId(repositoryDomainId)
        require(newState != RepositoryTrustDomainState.EXPIRED) { "Expiry is derived from verified metadata" }
        val currentState = state.get()
        val snapshot = currentState.snapshots[repositoryDomainId]
        if (snapshot == null) {
            val currentInactiveState = currentState.inactiveDomainStates[repositoryDomainId]
            require(currentInactiveState != RepositoryTrustDomainState.REVOKED || newState == RepositoryTrustDomainState.REVOKED) {
                "Revoked trust domain cannot be reactivated"
            }
            when (newState) {
                RepositoryTrustDomainState.SUSPENDED,
                RepositoryTrustDomainState.REVOKED,
                -> state.set(
                    currentState.copy(
                        inactiveDomainStates = currentState.inactiveDomainStates +
                            (repositoryDomainId to newState),
                    ),
                )
                RepositoryTrustDomainState.ACTIVE -> {
                    require(currentInactiveState == RepositoryTrustDomainState.SUSPENDED) {
                        "Unknown trust domain"
                    }
                    state.set(
                        currentState.copy(
                            inactiveDomainStates = currentState.inactiveDomainStates - repositoryDomainId,
                        ),
                    )
                }
                RepositoryTrustDomainState.EXPIRED -> error("Expiry is derived from verified metadata")
            }
            notifyChanged()
            return
        }
        require(snapshot.state != RepositoryTrustDomainState.REVOKED || newState == RepositoryTrustDomainState.REVOKED) {
            "Revoked trust domain cannot be reactivated"
        }
        state.set(
            currentState.copy(
                snapshots = currentState.snapshots + (repositoryDomainId to snapshot.copy(state = newState)),
                inactiveDomainStates = currentState.inactiveDomainStates - repositoryDomainId,
            ),
        )
        notifyChanged()
    }

    @Synchronized
    fun selectPackageDomain(packageName: String, repositoryDomainId: String?) {
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,200}")))
        repositoryDomainId?.let(::requireValidDomainId)
        val current = state.get()
        state.set(current.copy(packageSelections = current.packageSelections.withSelection(packageName, repositoryDomainId)))
        notifyChanged()
    }

    @Synchronized
    fun selectSourceDomain(sourceId: String, repositoryDomainId: String?) {
        require(sourceId.matches(Regex("[A-Za-z0-9._-]{1,160}")))
        repositoryDomainId?.let(::requireValidDomainId)
        val current = state.get()
        state.set(current.copy(sourceSelections = current.sourceSelections.withSelection(sourceId, repositoryDomainId)))
        notifyChanged()
    }

    fun domainStates(now: Long = System.currentTimeMillis()): Map<String, RepositoryTrustDomainState> =
        state.get().let { current ->
            current.inactiveDomainStates +
                current.snapshots.mapValues { (_, snapshot) -> snapshot.effectiveState(now) }
        }

    fun clear() {
        state.set(ProviderState())
        notifyChanged()
    }

    @Synchronized
    fun clear(repositoryDomainId: String) {
        requireValidDomainId(repositoryDomainId)
        val current = state.get()
        state.set(
            current.copy(
                snapshots = current.snapshots - repositoryDomainId,
                inactiveDomainStates = current.inactiveDomainStates - repositoryDomainId,
                packageSelections = current.packageSelections.filterValues { it != repositoryDomainId },
                sourceSelections = current.sourceSelections.filterValues { it != repositoryDomainId },
            ),
        )
        notifyChanged()
    }

    internal fun policyFor(
        packageName: String,
        sourceId: String,
        now: Long = System.currentTimeMillis(),
    ): ExtensionSigningPolicy? = policyForPackage(packageName, now)?.takeIf { sourceId in it.sources }

    internal fun policyForPackage(
        packageName: String,
        now: Long = System.currentTimeMillis(),
    ): ExtensionSigningPolicy? {
        val current = state.get()
        val activePolicies = current.snapshots.values
            .filter { it.effectiveState(now) == RepositoryTrustDomainState.ACTIVE }
            .flatMap { snapshot -> snapshot.policies }
        val packageCandidates = activePolicies.filter { it.packageName == packageName }
        if (packageCandidates.isEmpty()) return null

        val selectedPackageDomain = current.packageSelections[packageName]
        val packagePolicy = when {
            selectedPackageDomain != null ->
                packageCandidates.singleOrNull { it.repositoryDomainId == selectedPackageDomain } ?: return null
            packageCandidates.size == 1 -> packageCandidates.single()
            else -> return null
        }

        val everySourceOwnedBySelectedDomain = packagePolicy.sources.keys.all { sourceId ->
            val owners = activePolicies.filter { sourceId in it.sources }
            val selectedSourceDomain = current.sourceSelections[sourceId]
            if (selectedSourceDomain != null) {
                selectedSourceDomain == packagePolicy.repositoryDomainId
            } else if (owners.size == 1) {
                true
            } else {
                when {
                    owners.map(ExtensionSigningPolicy::packageName).distinct().size == 1 ->
                        selectedPackageDomain == packagePolicy.repositoryDomainId
                    else -> false
                }
            }
        }
        return packagePolicy.takeIf { everySourceOwnedBySelectedDomain }
    }

    private fun VerifiedExtensionTrustSnapshot.normalized() = copy(
        policies = policies.map { policy ->
            policy.copy(
                lineageAnchorsSha256 = policy.lineageAnchorsSha256.mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) },
                approvedCurrentSignersSha256 = policy.approvedCurrentSignersSha256
                    .mapTo(linkedSetOf()) { it.lowercase(Locale.ROOT) },
                targetSha256 = policy.targetSha256.lowercase(Locale.ROOT),
                acceptedArtifacts = policy.acceptedArtifacts.map { artifact ->
                    artifact.copy(sha256 = artifact.sha256.lowercase(Locale.ROOT))
                },
                sources = policy.sources.mapValues { (_, source) ->
                    source.copy(policyHash = source.policyHash.lowercase(Locale.ROOT))
                },
            )
        },
    )

    private data class ProviderState(
        val snapshots: Map<String, VerifiedExtensionTrustSnapshot> = emptyMap(),
        val inactiveDomainStates: Map<String, RepositoryTrustDomainState> = emptyMap(),
        val packageSelections: Map<String, String> = emptyMap(),
        val sourceSelections: Map<String, String> = emptyMap(),
    )

    private fun notifyChanged() {
        _changes.value = _changes.value + 1
    }
}

private fun <K, V> Map<K, V>.withSelection(key: K, value: V?): Map<K, V> =
    if (value == null) this - key else this + (key to value)

private fun validateSignedNetworkPolicy(policy: SourceNetworkPolicy) {
    val knownCapabilities = setOf(
        NamedHostCapabilities.RESOURCE_READ,
        NamedHostCapabilities.EXTERNAL_LINK,
        NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS,
        NamedHostCapabilities.EYNY_CHALLENGE_PROOF,
    )
    require(policy.policyVersion == 1 || policy.policyVersion == 2) {
        "Signed policy has an unsupported version"
    }
    val scopedHosts = listOf(
        policy.exactHosts,
        policy.resourceExactHosts,
        policy.externalExactHosts,
        policy.authExactHosts,
    )
    require(policy.exactHosts.isNotEmpty() && scopedHosts.all { it.size <= MAX_EXACT_HOSTS })
    require(policy.allExactHosts.size <= MAX_EXACT_HOSTS) {
        "Signed policy exceeds the combined Host limit"
    }
    require(scopedHosts.flatten().all { host ->
        host == host.lowercase(Locale.ROOT) &&
            !host.startsWith('.') && !host.endsWith('.') && ".." !in host && '*' !in host && ':' !in host &&
            host.length <= 253 && host.split('.').size >= 2 && host.split('.').all { label ->
                label.isNotEmpty() && label.length <= 63 &&
                    label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            } && !host.split('.').let { labels ->
                labels.size == 4 && labels.all { it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
            }
    }) { "Signed policy contains a non-canonical or wildcard host" }
    if (policy.policyVersion == 1) {
        require(
            policy.resourceExactHosts == policy.exactHosts &&
                policy.externalExactHosts == policy.exactHosts &&
                policy.authExactHosts == policy.exactHosts,
        ) { "Version 1 policy cannot express scoped hosts" }
    }
    if (policy.policyVersion == 1) {
        require(policy.operations.keys == setOf(NetworkOperations.SOURCE_READ)) {
            "Signed policy contains an unknown network operation"
        }
    } else {
        require(policy.operations.isEmpty()) { "Version 2 policy must use request rules" }
    }
    require(policy.requestRules.isNotEmpty() && policy.requestRules.size <= MAX_REQUEST_RULES)
    require(policy.requestRules.all { rule ->
        rule.exactHosts.isNotEmpty() && rule.exactHosts.all { it in policy.exactHosts } &&
            rule.operation.name == NetworkOperations.SOURCE_READ &&
            rule.operation.methods.isNotEmpty() &&
            rule.operation.methods.all { it == "GET" || it == "HEAD" } &&
            rule.operation.pathPrefixes.isNotEmpty() &&
            rule.operation.pathPrefixes.size <= MAX_PATH_PREFIXES
    }) { "Signed policy exceeds Host request-rule limits" }
    require(policy.requestRules.distinct().size == policy.requestRules.size) {
        "Signed policy contains duplicate request rules"
    }
    if (policy.policyVersion == 2) {
        require(policy.requestRules.flatMapTo(linkedSetOf()) { it.exactHosts } == policy.exactHosts) {
            "Version 2 request hosts do not match the rule host union"
        }
    }
    require(policy.namedCapabilities.all { it in knownCapabilities }) {
        "Signed policy contains an unknown named capability"
    }
    // canonicalJson performs the remaining deterministic path/name checks.
    policy.sha256()
}

private fun requireValidDomainId(value: String) {
    require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
        "Repository domain id must be a canonical UUID"
    }
}

private fun isSha256(value: String): Boolean = value.matches(Regex("[a-fA-F0-9]{64}"))

private const val MAX_EXTENSION_APK_BYTES = 64L * 1024 * 1024
private const val MAX_EXACT_HOSTS = 32
private const val MAX_PATH_PREFIXES = 32
private const val MAX_REQUEST_RULES = 32
private const val MAX_ACCEPTED_ARTIFACTS = 2
