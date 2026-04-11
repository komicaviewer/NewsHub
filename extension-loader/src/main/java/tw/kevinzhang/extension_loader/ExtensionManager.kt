package tw.kevinzhang.extension_loader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.PathClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val EXTENSION_META_KEY = "newshub.extension"
private const val SOURCE_CLASS_KEY = "newshub.extension.source_class"
private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".provider"

@Singleton
class ExtensionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceContext: SourceContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedExtensions = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installedExtensions: StateFlow<List<InstalledExtension>> = _installedExtensions.asStateFlow()

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
                val pkgName = intent.data?.schemeSpecificPart ?: return
                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REPLACED -> notifyPackageChanged(pkgName)

                    Intent.ACTION_PACKAGE_REMOVED -> notifyPackageRemoved(pkgName)
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
            _installedExtensions.value = scanInstalledExtensions()
        }
    }

    fun notifyPackageChanged(pkgName: String) {
        refreshAllExtensions()
    }

    fun notifyPackageRemoved(pkgName: String) {
        _installedExtensions.value = _installedExtensions.value
            .filter { it.pkgName != pkgName }
        // Then do a full rescan to be safe
        refreshAllExtensions()
    }

    /**
     * Creates an Intent to trigger the system package installer to install an APK file.
     * The APK must have been downloaded to the app's cache directory.
     * Requires android.permission.REQUEST_INSTALL_PACKAGES.
     */
    fun createInstallIntent(apkFile: File): Intent {
        val authority = context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    /**
     * Creates an Intent to trigger the system to uninstall the given extension package.
     * Must be started from an Activity context; FLAG_ACTIVITY_NEW_TASK is set so it
     * also works if the caller uses ApplicationContext.
     */
    @Suppress("DEPRECATION")
    fun createUninstallIntent(pkgName: String): Intent {
        return Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$pkgName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun scanInstalledExtensions(): List<InstalledExtension> {
        return context.packageManager
            .getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { pkg ->
                pkg.applicationInfo?.metaData?.containsKey(EXTENSION_META_KEY) == true
            }
            .mapNotNull { pkg -> loadExtension(pkg) }
    }


    private fun loadExtension(pkg: android.content.pm.PackageInfo): InstalledExtension? {
        return try {
            val appInfo = pkg.applicationInfo ?: return null
            val className = appInfo.metaData?.getString(SOURCE_CLASS_KEY) ?: return null
            val loader = PathClassLoader(appInfo.sourceDir, context.classLoader)
            val clazz = loader.loadClass(className)
            val source = clazz.getDeclaredConstructor().newInstance() as? Source ?: return null
            source.onAttach(sourceContext)

            InstalledExtension(
                pkgName = pkg.packageName,
                name = appInfo.metaData?.getString("newshub.extension.name") ?: source.name,
                versionName = pkg.versionName ?: "1.0",
                versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkg),
                lang = source.language,
                sources = listOf(source),
            )
        } catch (e: Exception) {
            null
        }
    }
}
