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
import java.io.File
import java.io.IOException
import java.security.MessageDigest
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
        okHttpClient.newCall(Request.Builder().url(indexUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Index fetch failed: HTTP ${response.code}")
            val body = response.body?.string() ?: return@withContext emptyList()
            gson.fromJson(body, ExtensionIndex::class.java).extensions
        }
    }

    override suspend fun downloadApk(apkUrl: String, expectedSha256: String?): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(apkUrl).build()
        val safeFilename = MessageDigest.getInstance("SHA-256")
            .digest(apkUrl.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".apk"
        val destFile = File(context.cacheDir, safeFilename)
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Empty response body for APK: $apkUrl")
            body.byteStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (expectedSha256 != null) {
            if (expectedSha256.length != 64 || !expectedSha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                destFile.delete()
                throw IOException("Malformed sha256 in extension index for $apkUrl: '$expectedSha256'")
            }
            val actualSha256 = destFile.readBytes().let { bytes ->
                MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                destFile.delete()
                throw IOException("APK integrity check failed: expected $expectedSha256 but got $actualSha256")
            }
        }
        destFile
    }

    override fun getInstallState(info: ExtensionInfo): InstallState {
        val pkg = try {
            context.packageManager.getPackageInfo(info.id, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return InstallState.NOT_INSTALLED
        }
        val installedVersion = PackageInfoCompat.getLongVersionCode(pkg)
        return if (installedVersion < info.version) InstallState.UPDATE_AVAILABLE
        else InstallState.INSTALLED
    }
}
