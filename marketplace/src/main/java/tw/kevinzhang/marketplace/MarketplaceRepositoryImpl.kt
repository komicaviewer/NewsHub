package tw.kevinzhang.marketplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tw.kevinzhang.marketplace.data.ExtensionIndex
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import javax.inject.Inject
import javax.inject.Named

class MarketplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    @Named("marketplaceIndexUrl") private val indexUrl: String,
) : MarketplaceRepository {

    override suspend fun fetchIndex(): List<ExtensionInfo> = withContext(Dispatchers.IO) {
        if (indexUrl.isBlank()) return@withContext emptyList()
        val response = okHttpClient.newCall(Request.Builder().url(indexUrl).build()).execute()
        val body = response.body?.string() ?: return@withContext emptyList()
        gson.fromJson(body, ExtensionIndex::class.java).extensions
    }

    override fun getInstallState(info: ExtensionInfo): InstallState {
        val pkg = try {
            context.packageManager.getPackageInfo(info.id, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return InstallState.NOT_INSTALLED
        }
        val installedVersion = PackageInfoCompat.getLongVersionCode(pkg).toInt()
        return if (installedVersion < info.version) InstallState.UPDATE_AVAILABLE
        else InstallState.INSTALLED
    }
}
