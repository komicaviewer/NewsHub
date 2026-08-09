package tw.kevinzhang.marketplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File
import javax.inject.Inject

class MarketplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val trustConsumer: VerifiedRepositoryTrustConsumer,
) : MarketplaceRepository {
    private val refreshMutex = Mutex()
    private val stateStore = PreferencesRepositoryStateStore(context)
    private val trustedClient by lazy {
        val embeddedRoot = context.assets.open(EMBEDDED_ROOT_ASSET).use { it.readBytes() }
        TrustedRepositoryClient(
            baseClient = okHttpClient,
            embeddedRoot = embeddedRoot,
            stateStore = stateStore,
        )
    }
    private var cachedRepoUrl: String? = null
    private var cachedAtMillis: Long = 0
    private var cachedSnapshot: RepositorySnapshot? = null

    init {
        // Every persisted snapshot is re-verified against its persisted threshold root before it
        // can authorize extension discovery. Corrupt or expired state simply leaves trust empty.
        runCatching { trustedClient.loadPersistedSnapshot() }
            .getOrNull()
            ?.let { snapshot ->
                cachedRepoUrl = OFFICIAL_REPO_URL
                cachedAtMillis = System.currentTimeMillis()
                cachedSnapshot = snapshot
                trustConsumer.install(snapshot.trust)
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

    override suspend fun downloadApk(info: ExtensionInfo): File =
        trustedClient.downloadAndVerify(context, info)

    private suspend fun refresh(repoUrl: String): RepositorySnapshot = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        cachedSnapshot
            ?.takeIf { cachedRepoUrl == repoUrl && now - cachedAtMillis <= MEMORY_CACHE_MILLIS }
            ?.let { return@withLock it }

        val refreshed = withContext(Dispatchers.IO) { trustedClient.refresh(repoUrl) }
        cachedRepoUrl = repoUrl
        cachedAtMillis = now
        cachedSnapshot = refreshed
        trustConsumer.install(refreshed.trust)
        refreshed
    }

    private companion object {
        const val EMBEDDED_ROOT_ASSET = "extension-root.json"
        const val OFFICIAL_REPO_URL = "https://github.com/komicaviewer/extensions"
        const val MEMORY_CACHE_MILLIS = 30_000L
    }
}

private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
