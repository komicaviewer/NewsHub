package tw.kevinzhang.newshub.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.AndroidEntryPoint
import tw.kevinzhang.extension_loader.ExtensionManager
import javax.inject.Inject

@AndroidEntryPoint
class ExtensionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var extensionManager: ExtensionManager

    override fun onReceive(context: Context, intent: Intent) {
        val pkgName = intent.data?.schemeSpecificPart ?: return

        // Only handle NewsHub extensions
        if (!isNewsHubExtension(context, pkgName)) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> extensionManager.notifyPackageChanged(pkgName)
            Intent.ACTION_PACKAGE_REMOVED -> extensionManager.notifyPackageRemoved(pkgName)
        }
    }

    private fun isNewsHubExtension(context: Context, pkgName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                pkgName, PackageManager.GET_META_DATA
            )
            appInfo.metaData?.containsKey("newshub.extension") == true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
