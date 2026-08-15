package tw.kevinzhang.extension_loader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.HostBrokerProvider
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.SourceIdentity
import java.io.File
import java.security.MessageDigest
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"
private const val TAG = "ExtensionManager"

data class QuarantinedExtension(
    val packageName: String,
    val serviceClassName: String?,
    val reason: String,
)

@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val brokerProvider: HostBrokerProvider,
    private val resourceProvider: HostResourceProvider,
    private val trustPolicyProvider: ExtensionTrustPolicyProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeConnections = mutableListOf<RemoteSourceConnection>()
    private val activeIdentities = mutableListOf<SourceIdentity>()
    private val refreshMutex = Mutex()
    private val refreshGeneration = AtomicLong()
    private val _installedExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installedExtensions: StateFlow<List<InstalledExtension>> = _installedExtensions.asStateFlow()
    private val _quarantinedExtensions = MutableStateFlow<List<QuarantinedExtension>>(emptyList())
    val quarantinedExtensions: StateFlow<List<QuarantinedExtension>> = _quarantinedExtensions.asStateFlow()

    init {
        refreshAllExtensions()
        registerPackageReceiver()
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> notifyPackageChanged(packageName)
                    Intent.ACTION_PACKAGE_REMOVED -> notifyPackageRemoved(packageName)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun refreshAllExtensions() {
        val generation = refreshGeneration.incrementAndGet()
        scope.launch {
            refresh(generation)
        }
    }

    /** Deterministic refresh boundary used by install orchestration and device-level verification. */
    suspend fun refreshAllExtensionsAndAwait() {
        refresh(refreshGeneration.incrementAndGet())
    }

    private suspend fun refresh(generation: Long) = refreshMutex.withLock {
        if (generation != refreshGeneration.get()) return@withLock
        synchronized(activeConnections) {
            // Revoke every Host capability before the explicit Service unbind.
            activeIdentities.forEach(resourceProvider::revoke)
            activeIdentities.clear()
            activeConnections.forEach(RemoteSourceConnection::close)
            activeConnections.clear()
        }
        // Never expose RemoteSource objects whose connections were just revoked.
        _installedExtensions.value = emptyList()
        val scan = scanInstalledExtensions()
        if (generation != refreshGeneration.get()) {
            scan.identities.forEach(resourceProvider::revoke)
            scan.connections.forEach(RemoteSourceConnection::close)
            return@withLock
        }
        synchronized(activeConnections) {
            activeConnections += scan.connections
            activeIdentities += scan.identities
        }
        _quarantinedExtensions.value = scan.quarantined
        _installedExtensions.value = scan.installed
    }

    fun notifyPackageChanged(packageName: String) = refreshAllExtensions()

    fun notifyPackageRemoved(packageName: String) {
        _installedExtensions.value = _installedExtensions.value.filter { it.pkgName != packageName }
        refreshAllExtensions()
    }

    /** Installation is only reachable for APKs already verified by the trusted repository layer. */
    @Suppress("DEPRECATION")
    fun installExtension(apkFile: File) {
        require(apkFile.isFile && apkFile.extension.equals("apk", ignoreCase = true)) {
            "Extension installer requires an APK file"
        }
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        })
    }

    @Suppress("DEPRECATION")
    fun uninstallExtension(packageName: String) {
        if (!isPackageInstalled(packageName) || !OfficialExtensionCatalog.isOfficialPackage(packageName)) return
        context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = "package:$packageName".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun scanInstalledExtensions(): ScanResult {
        verifyHostBindPermission()
        val candidateServices = querySourceServices().mapNotNull(ResolveInfo::serviceInfo)
        val quarantined = mutableListOf<QuarantinedExtension>()
        val accepted = candidateServices.groupBy { it.packageName }.flatMap { (packageName, services) ->
            runCatching {
                val signingPolicy = requireNotNull(trustPolicyProvider.policyForPackage(packageName)) {
                    "No current threshold-verified package policy"
                }
                val info = packageInfo(packageName)
                verifyInstalledPackageArtifact(info.toInstalledArtifact(), signingPolicy)
                val descriptors = services.map(ExtensionDescriptorValidator::fromServiceInfo)
                verifyExpectedServiceSet(descriptors, signingPolicy)
                val signingIdentity = verifiedSigningIdentity(packageName, info, signingPolicy)
                val packageMarker = info.toInstalledMarker(signingIdentity)
                descriptors.map { descriptor ->
                    val expectedService = requireNotNull(signingPolicy.sources[descriptor.sourceId]) {
                        "Source is absent from signed target"
                    }
                    verifyServiceDescriptor(descriptor, expectedService)
                    val policy = requireNotNull(
                        OfficialExtensionCatalog.policyFor(descriptor.packageName, descriptor.sourceId),
                    ) { "Package/Source is not in the official trust root" }
                    verifyExpectedNetworkPolicyHash(expectedService.policyHash, policy.networkPolicy())
                    Candidate(descriptor, policy, signingPolicy, signingIdentity, packageMarker)
                }
            }.onFailure { error ->
                services.forEach { service ->
                    quarantined += QuarantinedExtension(packageName, service.name, error.message.orEmpty())
                }
                Log.w(TAG, "Quarantining $packageName: ${error.message}")
            }.getOrDefault(emptyList())
        }.toMutableList()

        val duplicateSourceIds = accepted.groupBy { it.descriptor.sourceId }
            .filterValues { it.size != 1 }
            .keys
        val duplicateProcesses = accepted.groupBy { it.descriptor.packageName to it.descriptor.processName }
            .filterValues { it.size != 1 }
            .keys
        val unique = accepted.filter { candidate ->
            val descriptor = candidate.descriptor
            val reason = when {
                descriptor.sourceId in duplicateSourceIds -> "Duplicate global Source id"
                descriptor.packageName to descriptor.processName in duplicateProcesses -> "Sources must use distinct isolated processes"
                else -> null
            }
            if (reason != null) {
                quarantined += QuarantinedExtension(descriptor.packageName, descriptor.serviceClassName, reason)
                false
            } else {
                true
            }
        }

        val connections = mutableListOf<RemoteSourceConnection>()
        val identities = mutableListOf<SourceIdentity>()
        val sourcesByPackage = unique.groupBy { it.descriptor.packageName }
            .flatMap { (packageName, packageCandidates) ->
                val bound = mutableListOf<BoundCandidate>()
                var bindFailure: String? = null
                for (candidate in packageCandidates) {
                    val identity = SourceIdentity(
                        packageName,
                        candidate.signingIdentity.lineageAnchorSha256,
                        candidate.descriptor.sourceId,
                        candidate.signingIdentity.currentSignerSha256,
                    )
                    val connection = RemoteSourceConnection(context, candidate.descriptor) {
                        resourceProvider.revoke(identity)
                    }
                    if (!connection.bind()) {
                        bindFailure = "Explicit bind failed"
                        break
                    }
                    val unchangedAfterBind = runCatching {
                        val currentInfo = packageInfo(packageName)
                        val currentSigningIdentity = verifiedSigningIdentity(
                            packageName,
                            currentInfo,
                            candidate.signingPolicy,
                        )
                        verifyPackageUnchangedAfterBind(
                            candidate.packageMarker,
                            currentInfo.toInstalledMarker(currentSigningIdentity),
                        )
                    }
                    if (unchangedAfterBind.isFailure) {
                        connection.close()
                        resourceProvider.revoke(identity)
                        bindFailure = unchangedAfterBind.exceptionOrNull()?.message.orEmpty()
                        break
                    }
                    bound += BoundCandidate(candidate, identity, connection)
                }

                if (bindFailure != null || bound.size != packageCandidates.size) {
                    bound.forEach { item ->
                        item.connection.close()
                        resourceProvider.revoke(item.identity)
                    }
                    val reason = bindFailure ?: "Package bind did not complete"
                    packageCandidates.forEach { candidate ->
                        quarantined += QuarantinedExtension(
                            packageName,
                            candidate.descriptor.serviceClassName,
                            reason,
                        )
                    }
                    Log.w(TAG, "Quarantining $packageName after bind: $reason")
                    emptyList()
                } else {
                    connections += bound.map(BoundCandidate::connection)
                    identities += bound.map(BoundCandidate::identity)
                    bound.map { item ->
                        val candidate = item.candidate
                        val source = if (candidate.descriptor.needsLogin) {
                            RemoteAuthenticatedSource(
                                candidate.descriptor,
                                item.identity,
                                candidate.policy,
                                item.connection,
                                brokerProvider,
                                resourceProvider,
                            )
                        } else {
                            RemoteSource(
                                candidate.descriptor,
                                item.identity,
                                candidate.policy,
                                item.connection,
                                brokerProvider,
                                resourceProvider,
                            )
                        }
                        packageName to source
                    }
                }
            }.groupBy({ it.first }, { it.second })

        val installed = sourcesByPackage.map { (packageName, sources) ->
            val info = packageInfo(packageName)
            InstalledExtension(
                pkgName = packageName,
                name = packageName,
                versionName = info.versionName.orEmpty(),
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                lang = sources.map { it.language }.distinct().singleOrNull().orEmpty(),
                sources = sources,
            )
        }
        return ScanResult(installed, quarantined, connections, identities)
    }

    private fun verifyHostBindPermission() {
        val permission = context.packageManager.getPermissionInfo(ExtensionProtocol.BIND_PERMISSION, 0)
        require(permission.packageName == context.packageName) { "Host does not own bind permission" }
        require(permission.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_SIGNATURE) {
            "Extension bind permission must be signature protected"
        }
    }

    private fun querySourceServices(): List<ResolveInfo> {
        val intent = Intent(ExtensionProtocol.SERVICE_ACTION)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }
    }

    private fun verifiedSigningIdentity(
        packageName: String,
        info: PackageInfo,
        trustPolicy: ExtensionSigningPolicy,
    ): VerifiedSigningIdentity {
        require(OfficialExtensionCatalog.isOfficialPackage(packageName)) { "Unknown extension package" }
        require(info.packageName == packageName) { "PackageInfo belongs to a different package" }
        val signingInfo = requireNotNull(info.signingInfo) { "Package has no signing information" }
        require(!signingInfo.hasMultipleSigners()) { "Multi-signer extension packages are not supported" }
        val history = signingInfo.signingCertificateHistory.orEmpty().map(::certificateSha256).toSet()
        val currentSigners = signingInfo.apkContentsSigners.orEmpty().map(::certificateSha256).toSet()
        require(currentSigners.size == 1) { "Extension must have exactly one current signer" }
        val currentSigner = currentSigners.single()
        require(currentSigner in history) { "Current signer is outside the authorized lineage" }
        require(currentSigner in trustPolicy.approvedCurrentSignersSha256) {
            "Current extension signer is not approved"
        }
        val approvedAnchors = history.intersect(trustPolicy.lineageAnchorsSha256)
        require(approvedAnchors.size == 1) { "Extension signing lineage has no unique approved anchor" }
        return VerifiedSigningIdentity(
            lineageAnchorSha256 = approvedAnchors.single(),
            currentSignerSha256 = currentSigner,
        )
    }

    private fun certificateSha256(certificate: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(certificate.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun PackageInfo.toInstalledArtifact(): InstalledPackageArtifact {
        val appInfo = requireNotNull(applicationInfo) { "Extension package has no ApplicationInfo" }
        val sourceDir = appInfo.sourceDir?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Extension package has no base APK path")
        return InstalledPackageArtifact(
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            sourcePath = Paths.get(sourceDir),
            splitSourcePaths = buildList {
                splitNames?.let(::addAll)
                appInfo.splitSourceDirs?.let(::addAll)
            },
            requestedPermissions = requestedPermissions.orEmpty().toList(),
        )
    }

    private fun PackageInfo.toInstalledMarker(
        signingIdentity: VerifiedSigningIdentity,
    ): InstalledPackageMarker {
        val appInfo = requireNotNull(applicationInfo) { "Extension package has no ApplicationInfo" }
        return InstalledPackageMarker(
            versionCode = PackageInfoCompat.getLongVersionCode(this),
            sourceDir = appInfo.sourceDir.orEmpty(),
            splitNames = splitNames.orEmpty().toList(),
            splitSourceDirs = appInfo.splitSourceDirs.orEmpty().toList(),
            lastUpdateTime = lastUpdateTime,
            lineageAnchorSha256 = signingIdentity.lineageAnchorSha256,
            currentSignerSha256 = signingIdentity.currentSignerSha256,
        )
    }

    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS).toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS,
            )
        }

    private data class Candidate(
        val descriptor: ExtensionDescriptor,
        val policy: OfficialSourcePolicy,
        val signingPolicy: ExtensionSigningPolicy,
        val signingIdentity: VerifiedSigningIdentity,
        val packageMarker: InstalledPackageMarker,
    )

    private data class BoundCandidate(
        val candidate: Candidate,
        val identity: SourceIdentity,
        val connection: RemoteSourceConnection,
    )

    private data class VerifiedSigningIdentity(
        val lineageAnchorSha256: String,
        val currentSignerSha256: String,
    )

    private data class ScanResult(
        val installed: List<InstalledExtension>,
        val quarantined: List<QuarantinedExtension>,
        val connections: List<RemoteSourceConnection>,
        val identities: List<SourceIdentity>,
    )
}
