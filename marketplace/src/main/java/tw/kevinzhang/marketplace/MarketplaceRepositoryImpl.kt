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
import okhttp3.Request
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class MarketplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val trustConsumer: VerifiedRepositoryTrustConsumer,
) : MarketplaceRepository {
    private val refreshMutex = Mutex()
    private val embeddedRoot by lazy { context.assets.open(EMBEDDED_ROOT_ASSET).use { it.readBytes() } }
    override val officialRepositoryDomain: RepositoryTrustDomain by lazy {
        RepositoryTrustDomains.official(embeddedRoot)
    }
    private val trustDirectory by lazy { File(context.filesDir, "extension-trust") }
    private val inspectionClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .proxy(Proxy.NO_PROXY)
        .cache(null)
        .build()
    private val baseClient = okHttpClient
    private val pendingInspections = linkedMapOf<String, PendingInspection>()
    private val runtimes = linkedMapOf<String, DomainRuntime>()

    init {
        val official = officialRepositoryDomain
        val store = RepositoryTrustDomainStore(trustDirectory, official.id)
        store.saveBootstrapRoot(embeddedRoot)
        val runtime = newRuntime(official, embeddedRoot, store)
        synchronized(runtimes) { runtimes[official.id] = runtime }
        restorePersistedSnapshot(runtime)
    }

    override suspend fun inspectRepositoryRoot(repoUrl: String): RepositoryRootPreview =
        withContext(Dispatchers.IO) {
            val canonical = canonicalRepositoryBaseUrl(repoUrl).toString().trimEnd('/')
            synchronized(runtimes) {
                if (runtimes.values.any { it.domain.canonicalBaseUrl == canonical }) {
                    throw TrustedMetadataException("Repository source is already trusted")
                }
            }
            val rootUrl = repositoryRootUrl(canonical)
            val bytes = inspectionClient.newCall(Request.Builder().url(rootUrl).get().build())
                .execute()
                .use { response ->
                    if (response.request.url != rootUrl || response.isRedirect) {
                        throw TrustedMetadataException("Repository root redirected outside the approved address")
                    }
                    if (!response.isSuccessful) {
                        throw IOException("Root metadata fetch failed: HTTP ${response.code}")
                    }
                    val declaredLength = response.body?.contentLength() ?: -1L
                    if (declaredLength > MAX_BOOTSTRAP_ROOT_BYTES) {
                        throw TrustedMetadataException("Repository root is too large")
                    }
                    response.body?.byteStream()?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BOOTSTRAP_ROOT_BYTES) {
                                throw TrustedMetadataException("Repository root is too large")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    } ?: throw IOException("Repository root response is empty")
                }
            // Constructor verification proves that the downloaded root meets its own root role
            // threshold before the user is shown fingerprints.
            TrustedMetadataVerifier(bytes, Instant::now)
            val inspection = inspectTrustedRoot(bytes)
            val token = UUID.randomUUID().toString()
            synchronized(pendingInspections) {
                pendingInspections[token] = PendingInspection(canonical, bytes, inspection)
            }
            RepositoryRootPreview(
                confirmationToken = token,
                canonicalBaseUrl = canonical,
                rootThreshold = inspection.threshold,
                rootKeyFingerprints = inspection.keyFingerprints,
            )
        }

    override suspend fun confirmRepositoryRoot(confirmationToken: String): RepositoryTrustDomain {
        val pending = synchronized(pendingInspections) {
            pendingInspections.remove(confirmationToken)
        } ?: throw TrustedMetadataException("Repository confirmation has expired")
        val domain = RepositoryTrustDomain(
            id = UUID.randomUUID().toString(),
            canonicalBaseUrl = pending.canonicalBaseUrl,
            trustMode = RepositoryTrustMode.USER_PINNED,
            state = RepositoryDomainState.ACTIVE,
            rootThreshold = pending.inspection.threshold,
            rootKeyFingerprints = pending.inspection.keyFingerprints,
        )
        val store = RepositoryTrustDomainStore(trustDirectory, domain.id)
        return try {
            store.saveBootstrapRoot(pending.rootBytes)
            val runtime = newRuntime(domain, pending.rootBytes, store)
            val snapshot = withContext(Dispatchers.IO) {
                runtime.client.refresh(domain.canonicalBaseUrl)
            }
            runtime.cache(domain.canonicalBaseUrl, snapshot)
            trustConsumer.install(snapshot.trust)
            synchronized(runtimes) {
                check(runtimes.values.none { it.domain.canonicalBaseUrl == domain.canonicalBaseUrl }) {
                    "Repository source is already trusted"
                }
                runtimes[domain.id] = runtime
            }
            domain
        } catch (error: Exception) {
            store.deleteUncommittedDomain()
            throw error
        }
    }

    override fun cancelRepositoryRootInspection(confirmationToken: String) {
        synchronized(pendingInspections) { pendingInspections.remove(confirmationToken) }
    }

    override fun registerRepositoryDomains(domains: Collection<RepositoryTrustDomain>) {
        val normalized = (domains + officialRepositoryDomain).associateBy(RepositoryTrustDomain::id)
        normalized.values.forEach { domain ->
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
            synchronized(runtimes) { runtimes[domain.id] = runtime }
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
        return runtime.client.downloadAndVerify(context, info)
    }

    private suspend fun refresh(repoUrl: String): RepositorySnapshot = refreshMutex.withLock {
        val runtime = runtimeFor(repoUrl)
        if (runtime.domain.state != RepositoryDomainState.ACTIVE) {
            throw TrustedMetadataException("Repository trust is ${runtime.domain.state}")
        }
        val now = System.currentTimeMillis()
        runtime.cachedSnapshot
            ?.takeIf { runtime.cachedRepoUrl == repoUrl && now - runtime.cachedAtMillis <= MEMORY_CACHE_MILLIS }
            ?.let { return@withLock it }

        val refreshed = withContext(Dispatchers.IO) { runtime.client.refresh(runtime.domain.canonicalBaseUrl) }
        runtime.cache(repoUrl, refreshed)
        trustConsumer.install(refreshed.trust)
        refreshed
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
        ),
    )

    private fun restorePersistedSnapshot(runtime: DomainRuntime) {
        runCatching { runtime.client.loadPersistedSnapshot() }
            .getOrNull()
            ?.let { snapshot ->
                runtime.cache(runtime.domain.canonicalBaseUrl, snapshot)
                trustConsumer.install(snapshot.trust)
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

private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
