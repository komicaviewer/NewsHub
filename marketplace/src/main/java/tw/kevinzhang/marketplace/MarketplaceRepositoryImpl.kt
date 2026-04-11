package tw.kevinzhang.marketplace

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tw.kevinzhang.marketplace.data.AvailableSource
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.InstallState
import tw.kevinzhang.marketplace.data.RemoteExtensionDto
import tw.kevinzhang.marketplace.data.RepoMetadata
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

class MarketplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) : MarketplaceRepository {

    override suspend fun fetchRepoMetadata(repoUrl: String): RepoMetadata = withContext(Dispatchers.IO) {
        val rawBase = toRawBase(repoUrl)
        val json = fetchString("$rawBase/repo.json")
        gson.fromJson(json, RepoMetadata::class.java)
    }

    override suspend fun fetchExtensions(repoUrl: String): List<ExtensionInfo> = withContext(Dispatchers.IO) {
        val rawBase = toRawBase(repoUrl)
        val json = fetchString("$rawBase/index.min.json")
        val type = object : TypeToken<List<RemoteExtensionDto>>() {}.type
        val dtos: List<RemoteExtensionDto> = gson.fromJson(json, type)
        dtos.map { it.toExtensionInfo(rawBase) }
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

    override suspend fun downloadApk(apkUrl: String, expectedSha256: String?): File = withContext(Dispatchers.IO) {
        val safeFilename = MessageDigest.getInstance("SHA-256")
            .digest(apkUrl.toByteArray())
            .joinToString("") { "%02x".format(it) } + ".apk"
        val destFile = File(context.cacheDir, safeFilename)
        val bytes = fetchBytes(apkUrl)
        if (expectedSha256 != null) {
            if (expectedSha256.length != 64 || !expectedSha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                throw IOException("Malformed sha256 in extension index for $apkUrl: '$expectedSha256'")
            }
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("APK integrity check failed: expected $expectedSha256 but got $actualSha256")
            }
        }
        destFile.writeBytes(bytes)
        destFile
    }

    // Converts https://github.com/owner/repo → https://raw.githubusercontent.com/owner/repo/main
    private fun toRawBase(repoUrl: String): String {
        val normalized = repoUrl.trimEnd('/')
        return if (normalized.contains("raw.githubusercontent.com")) {
            normalized
        } else {
            normalized
                .replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("http://github.com/", "https://raw.githubusercontent.com/") +
                    "/main"
        }
    }

    private fun fetchString(url: String): String {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Fetch failed: HTTP ${response.code} for $url")
            return response.body?.string() ?: throw IOException("Empty response for $url")
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Fetch failed: HTTP ${response.code} for $url")
            return response.body?.bytes() ?: throw IOException("Empty response for $url")
        }
    }
}

private fun RemoteExtensionDto.toExtensionInfo(rawBase: String) = ExtensionInfo(
    id = pkg,
    name = name,
    version = versionCode.toLong(),
    versionName = versionName,
    language = lang,
    iconUrl = "$rawBase/icon/$iconName",
    apkUrl = "$rawBase/apk/$apkName",
    sha256 = sha256.ifBlank { null },
    sources = sources.map { AvailableSource(it.id, it.name, it.lang, it.baseUrl) },
)
