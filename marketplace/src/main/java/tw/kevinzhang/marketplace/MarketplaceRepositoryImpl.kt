package tw.kevinzhang.marketplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class MarketplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @javax.inject.Named("repositoryHttpClient") okHttpClient: OkHttpClient,
    private val trustConsumer: VerifiedRepositoryTrustConsumer,
    private val credentialStore: RepositoryCredentialStore,
) : MarketplaceRepository {
    private val refreshMutex = Mutex()
    private val embeddedRoot by lazy { context.assets.open(EMBEDDED_ROOT_ASSET).use { it.readBytes() } }
    override val officialRepositoryDomain: RepositoryTrustDomain by lazy {
        RepositoryTrustDomains.official(embeddedRoot)
    }
    private val trustDirectory by lazy { File(context.filesDir, "extension-trust") }
    private val baseClient = okHttpClient
    private val pendingInspections = linkedMapOf<String, PendingInspection>()
    private val runtimes = linkedMapOf<String, DomainRuntime>()
    private val discardedDomains = mutableSetOf<String>()

    init {
        val official = officialRepositoryDomain
        val store = RepositoryTrustDomainStore(trustDirectory, official.id)
        store.saveBootstrapRoot(embeddedRoot)
        val runtime = newRuntime(official, embeddedRoot, store)
        synchronized(runtimes) { runtimes[official.id] = runtime }
        restorePersistedSnapshot(runtime)
    }

    override suspend fun inspectRepositoryRoot(repoUrl: String): RepositoryRootPreview =
        inspectRepositoryRoot(RepositoryAccessDraft(repoUrl), credential = null)

    override suspend fun inspectRepositoryRoot(
        draft: RepositoryAccessDraft,
        credential: RepositoryAccessCredential?,
    ): RepositoryRootPreview =
        withContext(Dispatchers.IO) {
            val canonical = canonicalRepositoryBaseUrl(draft.repositoryUrl).toString().trimEnd('/')
            if (draft.access.kind == RepositoryAccessKind.GITHUB_CONTENTS) {
                requireGithubRepositoryBaseUrl(canonical)
            }
            synchronized(runtimes) {
                if (runtimes.values.any { it.domain.canonicalBaseUrl == canonical }) {
                    throw TrustedMetadataException("Repository source is already trusted")
                }
            }
            val rootUrl = repositoryRootUrl(canonical)
            val transport = RepositoryContentTransport(
                baseClient = baseClient,
                canonicalBaseUrl = canonical,
                access = draft.access,
                domainId = null,
                credentialProvider = { credential },
            )
            val bytes = transport.fetchRequired(rootUrl, MAX_BOOTSTRAP_ROOT_BYTES)
            credential?.ensureCurrent()
            // Constructor verification proves that the downloaded root meets its own root role
            // threshold before the user is shown fingerprints.
            TrustedMetadataVerifier(bytes, Instant::now)
            val inspection = inspectTrustedRoot(bytes)
            val token = UUID.randomUUID().toString()
            synchronized(pendingInspections) {
                pendingInspections[token] = PendingInspection(
                    canonicalBaseUrl = canonical,
                    rootBytes = bytes,
                    inspection = inspection,
                    access = draft.access,
                    credential = credential,
                )
            }
            RepositoryRootPreview(
                confirmationToken = token,
                canonicalBaseUrl = canonical,
                rootThreshold = inspection.threshold,
                rootKeyFingerprints = inspection.keyFingerprints,
                access = draft.access,
            )
        }

    override suspend fun confirmRepositoryRoot(confirmationToken: String): RepositoryTrustDomain {
        val pending = synchronized(pendingInspections) {
            pendingInspections.remove(confirmationToken)
        } ?: throw TrustedMetadataException("Repository confirmation has expired")
        var confirmed: RepositoryTrustDomain? = null
        try {
            return if (pending.access.credentialBinding == RepositoryCredentialBinding.GITHUB_ACCOUNT) {
                requireNotNull(pending.credential).authorized { confirmPendingRoot(pending).also { confirmed = it } }
            } else confirmPendingRoot(pending)
        } catch (error: Exception) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                confirmed?.let { discardUncommittedRepositoryDomain(it.id) }
            }
            throw error
        }
    }

    private suspend fun confirmPendingRoot(pending: PendingInspection): RepositoryTrustDomain {
        pending.credential?.ensureCurrent()
        val domain = RepositoryTrustDomain(
            id = UUID.randomUUID().toString(),
            canonicalBaseUrl = pending.canonicalBaseUrl,
            trustMode = RepositoryTrustMode.USER_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = pending.inspection.threshold,
            rootKeyFingerprints = pending.inspection.keyFingerprints,
            access = pending.access,
        )
        val store = RepositoryTrustDomainStore(trustDirectory, domain.id)
        var credentialSaved = false
        return try {
            if (domain.access.credentialBinding == RepositoryCredentialBinding.MANUAL_PAT) {
                val credential = pending.credential ?: throw RepositoryAccessRequiredException(
                    domain.id,
                    RepositoryAccessFailureReason.MISSING_CREDENTIAL,
                )
                credentialStore.saveCredential(domain.id, credential)
                credentialSaved = true
            }
            store.saveBootstrapRoot(pending.rootBytes)
            val runtime = newRuntime(domain, pending.rootBytes, store)
            val snapshot = withContext(Dispatchers.IO) {
                runtime.client.refresh(domain.canonicalBaseUrl)
            }
            runtime.cache(domain.canonicalBaseUrl, snapshot)
            pending.credential?.ensureCurrent()
            trustConsumer.install(snapshot.trust)
            synchronized(runtimes) {
                check(runtimes.values.none { it.domain.canonicalBaseUrl == domain.canonicalBaseUrl }) {
                    "Repository source is already trusted"
                }
                runtimes[domain.id] = runtime
            }
            domain
        } catch (error: Exception) {
            trustConsumer.setDomainState(domain.id, RepositoryDomainState.REVOKED)
            store.deleteUncommittedDomain()
            if (credentialSaved) runCatching { credentialStore.deleteCredential(domain.id) }
            throw error
        }
    }

    override fun cancelRepositoryRootInspection(confirmationToken: String) {
        synchronized(pendingInspections) { pendingInspections.remove(confirmationToken) }
    }

    override suspend fun discardUncommittedRepositoryDomain(domainId: String) {
        require(domainId != RepositoryTrustDomains.OFFICIAL_ID)
        val runtime = synchronized(runtimes) {
            discardedDomains.add(domainId)
            trustConsumer.setDomainState(domainId, RepositoryDomainState.REVOKED)
            runtimes.remove(domainId)
        }
        (runtime?.store ?: RepositoryTrustDomainStore(trustDirectory, domainId)).deleteUncommittedDomain()
        credentialStore.deleteCredential(domainId)
    }

    override suspend fun verifyRepositoryAccess(
        domainId: String,
        credential: RepositoryAccessCredential,
    ) {
        val runtime = synchronized(runtimes) { runtimes[domainId] }
            ?: throw TrustedMetadataException("Unknown repository trust domain")
        if (runtime.domain.access.kind != RepositoryAccessKind.GITHUB_CONTENTS) {
            throw TrustedMetadataException("Repository does not use authenticated GitHub access")
        }
        val verifier = TrustedRepositoryClient(
            baseClient = baseClient,
            embeddedRoot = runtime.bootstrapRoot,
            stateStore = NonPersistingRepositoryStateStore(runtime.store),
            domain = runtime.domain,
            credentialProvider = { credential },
        )
        withContext(Dispatchers.IO) { verifier.refresh(runtime.domain.canonicalBaseUrl) }
    }

    override fun registerRepositoryDomains(domains: Collection<RepositoryTrustDomain>) {
        val normalized = (domains + officialRepositoryDomain).associateBy(RepositoryTrustDomain::id)
        normalized.values.forEach { domain ->
            if (synchronized(runtimes) { domain.id in discardedDomains }) return@forEach
            val existing = synchronized(runtimes) { runtimes[domain.id] }
            if (existing?.domain == domain) return@forEach
            if (domain.id == RepositoryTrustDomains.OFFICIAL_ID) {
                require(domain.trustMode == RepositoryTrustMode.BUILTIN_PINNED)
            }
            val store = RepositoryTrustDomainStore(trustDirectory, domain.id)
            val bootstrapRoot = store.loadBootstrapRoot()
                ?: if (domain.id == RepositoryTrustDomains.OFFICIAL_ID) embeddedRoot else null
            if (bootstrapRoot == null) return@forEach
            val runtime = newRuntime(domain, bootstrapRoot, store)
            synchronized(runtimes) {
                if (domain.id in discardedDomains) return@forEach
                runtimes[domain.id] = runtime
            }
            if (domain.state == RepositoryDomainState.ACTIVE) {
                restorePersistedSnapshot(runtime)
            } else {
                trustConsumer.setDomainState(domain.id, domain.state)
            }
        }
    }

    override fun setRepositoryDomainState(domain: RepositoryTrustDomain) {
        require(domain.id != RepositoryTrustDomains.OFFICIAL_ID || domain.state != RepositoryDomainState.REVOKED) {
            "The built-in repository cannot be revoked"
        }
        val previous = synchronized(runtimes) { runtimes[domain.id] }
            ?: throw TrustedMetadataException("Unknown repository trust domain")
        require(previous.domain.state != RepositoryDomainState.REVOKED || domain.state == RepositoryDomainState.REVOKED) {
            "A revoked repository cannot be restored"
        }
        val runtime = newRuntime(domain, previous.bootstrapRoot, previous.store)
        synchronized(runtimes) { runtimes[domain.id] = runtime }
        if (domain.state == RepositoryDomainState.ACTIVE) {
            // Explicitly restore the user's domain state before reinstalling its verified
            // snapshot; metadata refresh alone must never silently undo or preserve suspension.
            trustConsumer.setDomainState(domain.id, RepositoryDomainState.ACTIVE)
            restorePersistedSnapshot(runtime)
        } else {
            trustConsumer.setDomainState(domain.id, domain.state)
        }
    }

    override suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata =
        refresh(repoUrl).repository

    override suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo> =
        refresh(repoUrl).extensions

    override fun getInstallState(info: ExtensionInfo): InstallState {
        val packageInfo = try {
            context.packageManager.getPackageInfo(
                info.id,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            return InstallState.NOT_INSTALLED
        }
        val signingInfo = packageInfo.signingInfo ?: return InstallState.NOT_INSTALLED
        if (signingInfo.hasMultipleSigners()) return InstallState.NOT_INSTALLED
        val currentPin = signingInfo.apkContentsSigners.singleOrNull()
            ?.toByteArray()
            ?.sha256Hex()
            ?: return InstallState.NOT_INSTALLED
        if (currentPin !in info.signerPins) return InstallState.NOT_INSTALLED
        val installedVersion = PackageInfoCompat.getLongVersionCode(packageInfo)
        return if (installedVersion < info.version) InstallState.UPDATE_AVAILABLE else InstallState.INSTALLED
    }

    override suspend fun downloadApk(info: ExtensionInfo): File {
        val runtime = synchronized(runtimes) { runtimes[info.repositoryDomainId] }
            ?: throw TrustedMetadataException("Unknown repository trust domain")
        var downloaded: File? = null
        return try { withAccess(runtime) {
            runtime.client.downloadAndVerify(context, info).also { downloaded = it }
        } } catch (error: Exception) { downloaded?.delete(); throw error }
    }

    override suspend fun withRepositoryAccess(domainId: String, action: suspend () -> Unit) {
        val runtime = synchronized(runtimes) { runtimes[domainId] }
            ?: throw TrustedMetadataException("Unknown repository trust domain")
        withAccess(runtime, action)
    }

    private suspend fun <T> withAccess(runtime: DomainRuntime, action: suspend () -> T): T {
        if (runtime.domain.state != RepositoryDomainState.ACTIVE) throw TrustedMetadataException("Repository is not active")
        if (runtime.domain.access.kind != RepositoryAccessKind.GITHUB_CONTENTS) return action()
        val credential = credentialStore.getCredential(runtime.domain) ?: throw RepositoryAccessRequiredException(
            runtime.domain.id, RepositoryAccessFailureReason.MISSING_CREDENTIAL)
        return credential.authorized(action)
    }

    private suspend fun refresh(repoUrl: String): RepositorySnapshot = refreshMutex.withLock {
        val runtime = runtimeFor(repoUrl)
        withAccess(runtime) { refreshRuntime(runtime, repoUrl) }
    }

    private suspend fun refreshRuntime(runtime: DomainRuntime, repoUrl: String): RepositorySnapshot {
        if (runtime.domain.state != RepositoryDomainState.ACTIVE) {
            throw TrustedMetadataException("Repository trust is ${runtime.domain.state}")
        }
        val now = System.currentTimeMillis()
        runtime.cachedSnapshot
            ?.takeIf { runtime.cachedRepoUrl == repoUrl && now - runtime.cachedAtMillis <= MEMORY_CACHE_MILLIS }
            ?.let { return it }

        val refreshed = withContext(Dispatchers.IO) { runtime.client.refresh(runtime.domain.canonicalBaseUrl) }
        runtime.cache(repoUrl, refreshed)
        trustConsumer.install(refreshed.trust)
        return refreshed
    }

    private fun runtimeFor(repoUrl: String): DomainRuntime {
        val normalized = repoUrl.trim().trimEnd('/')
        if (normalized == OFFICIAL_REPO_URL || normalized == RepositoryTrustDomains.OFFICIAL_BASE_URL) {
            return synchronized(runtimes) { runtimes.getValue(RepositoryTrustDomains.OFFICIAL_ID) }
        }
        val canonical = canonicalRepositoryBaseUrl(repoUrl).toString().trimEnd('/')
        return synchronized(runtimes) {
            runtimes.values.singleOrNull { it.domain.canonicalBaseUrl == canonical }
        } ?: throw TrustedMetadataException("Repository source has not been trusted")
    }

    private fun newRuntime(
        domain: RepositoryTrustDomain,
        bootstrapRoot: ByteArray,
        store: RepositoryTrustDomainStore,
    ) = DomainRuntime(
        domain = domain,
        bootstrapRoot = bootstrapRoot,
        store = store,
        client = TrustedRepositoryClient(
            baseClient = baseClient,
            embeddedRoot = bootstrapRoot,
            stateStore = store,
            domain = domain,
            credentialProvider = {
                if (domain.access.kind == RepositoryAccessKind.GITHUB_CONTENTS) {
                    credentialStore.getCredential(domain)
                } else {
                    null
                }
            },
        ),
    )

    private fun restorePersistedSnapshot(runtime: DomainRuntime) {
        runCatching { runtime.client.loadPersistedSnapshot() }
            .getOrNull()
            ?.let { snapshot ->
                synchronized(runtimes) {
                    if (runtime.domain.id !in discardedDomains) {
                        runtime.cache(runtime.domain.canonicalBaseUrl, snapshot)
                        trustConsumer.install(snapshot.trust)
                    }
                }
            }
    }

    private fun repositoryRootUrl(canonicalBaseUrl: String): HttpUrl =
        canonicalRepositoryBaseUrl(canonicalBaseUrl)
            .newBuilder()
            .addPathSegments("metadata/root.json")
            .build()

    private data class PendingInspection(
        val canonicalBaseUrl: String,
        val rootBytes: ByteArray,
        val inspection: RootTrustInspection,
        val access: RepositoryAccessDescriptor,
        val credential: RepositoryAccessCredential?,
    )

    private data class DomainRuntime(
        val domain: RepositoryTrustDomain,
        val bootstrapRoot: ByteArray,
        val store: RepositoryTrustDomainStore,
        val client: TrustedRepositoryClient,
        var cachedRepoUrl: String? = null,
        var cachedAtMillis: Long = 0,
        var cachedSnapshot: RepositorySnapshot? = null,
    ) {
        fun cache(repoUrl: String, snapshot: RepositorySnapshot) {
            cachedRepoUrl = repoUrl
            cachedAtMillis = System.currentTimeMillis()
            cachedSnapshot = snapshot
        }
    }

    private companion object {
        const val EMBEDDED_ROOT_ASSET = "extension-root.json"
        const val OFFICIAL_REPO_URL = "https://github.com/komicaviewer/extensions"
        const val MEMORY_CACHE_MILLIS = 30_000L
        const val MAX_BOOTSTRAP_ROOT_BYTES = 1_048_576L
    }
}

private class NonPersistingRepositoryStateStore(
    private val delegate: RepositoryStateStore,
) : RepositoryStateStore {
    override fun loadVersions(): RepositoryVersions = delegate.loadVersions()
    override fun loadRoot(): ByteArray? = delegate.loadRoot()
    override fun loadTargets(): ByteArray? = delegate.loadTargets()

    override fun save(
        root: ByteArray,
        timestamp: ByteArray,
        snapshot: ByteArray,
        targets: ByteArray,
        versions: RepositoryVersions,
    ) = Unit
}

private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
