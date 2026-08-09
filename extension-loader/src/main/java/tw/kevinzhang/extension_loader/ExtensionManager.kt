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
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.HostBrokerProvider
import tw.kevinzhang.extension_api.HostResourceProvider
import java.io.File
import java.security.MessageDigest
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeConnections = mutableListOf<RemoteSourceConnection>()
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
        scope.launch {
            val scan = scanInstalledExtensions()
            synchronized(activeConnections) {
                activeConnections.forEach(RemoteSourceConnection::close)
                activeConnections.clear()
                activeConnections += scan.connections
            }
            _quarantinedExtensions.value = scan.quarantined
            _installedExtensions.value = scan.installed
        }
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
        val candidates = querySourceServices()
        val quarantined = mutableListOf<QuarantinedExtension>()
        val accepted = candidates.mapNotNull { resolveInfo ->
            val service = resolveInfo.serviceInfo ?: return@mapNotNull null
            runCatching {
                val descriptor = ExtensionDescriptorValidator.fromServiceInfo(service)
                val policy = requireNotNull(
                    OfficialExtensionCatalog.policyFor(descriptor.packageName, descriptor.sourceId),
                ) { "Package/Source is not in the official trust root" }
                val signer = verifiedSigner(descriptor.packageName)
                Candidate(descriptor, policy, signer)
            }.onFailure { error ->
                quarantined += QuarantinedExtension(service.packageName, service.name, error.message.orEmpty())
                Log.w(TAG, "Quarantining ${service.packageName}/${service.name}: ${error.message}")
            }.getOrNull()
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
        val sourcesByPackage = unique.mapNotNull { candidate ->
            val connection = RemoteSourceConnection(context, candidate.descriptor)
            if (!connection.bind()) {
                quarantined += QuarantinedExtension(
                    candidate.descriptor.packageName,
                    candidate.descriptor.serviceClassName,
                    "Explicit bind failed",
                )
                return@mapNotNull null
            }
            connections += connection
            val source = if (candidate.descriptor.needsLogin) {
                RemoteAuthenticatedSource(
                    candidate.descriptor,
                    candidate.signerSha256,
                    candidate.policy,
                    connection,
                    brokerProvider,
                    resourceProvider,
                )
            } else {
                RemoteSource(
                    candidate.descriptor,
                    candidate.signerSha256,
                    candidate.policy,
                    connection,
                    brokerProvider,
                    resourceProvider,
                )
            }
            candidate.descriptor.packageName to source
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
        return ScanResult(installed, quarantined, connections)
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

    private fun verifiedSigner(packageName: String): String {
        require(OfficialExtensionCatalog.isOfficialPackage(packageName)) { "Unknown extension package" }
        val info = packageInfo(packageName)
        val signingInfo = requireNotNull(info.signingInfo) { "Package has no signing information" }
        require(!signingInfo.hasMultipleSigners()) { "Multi-signer extension packages are not supported" }
        val history = signingInfo.signingCertificateHistory.orEmpty().map { certificate ->
            MessageDigest.getInstance("SHA-256")
                .digest(certificate.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        require(OfficialExtensionCatalog.RELEASE_SIGNER_SHA256 in history) {
            "Extension signer is not trusted"
        }
        return history.last()
    }

    private fun packageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private data class Candidate(
        val descriptor: ExtensionDescriptor,
        val policy: OfficialSourcePolicy,
        val signerSha256: String,
    )

    private data class ScanResult(
        val installed: List<InstalledExtension>,
        val quarantined: List<QuarantinedExtension>,
        val connections: List<RemoteSourceConnection>,
    )
}
