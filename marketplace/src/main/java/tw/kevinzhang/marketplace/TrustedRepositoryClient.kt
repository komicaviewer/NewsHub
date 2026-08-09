package tw.kevinzhang.marketplace

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.pm.PackageInfoCompat
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import tw.kevinzhang.marketplace.data.AvailableSource
import tw.kevinzhang.marketplace.data.ExtensionInfo
import tw.kevinzhang.marketplace.data.RepoMetadata
import tw.kevinzhang.extension_api.ExtensionProtocol
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

internal data class RepositorySnapshot(
    val repository: RepoMetadata,
    val extensions: List<ExtensionInfo>,
    val trust: VerifiedRepositoryTrustSnapshot,
)

internal data class RepositoryVersions(
    val root: Long = 0,
    val timestamp: Long = 0,
    val snapshot: Long = 0,
    val targets: Long = 0,
    val trustedUntilEpochMillis: Long = 0,
)

internal interface RepositoryStateStore {
    fun loadVersions(): RepositoryVersions
    fun loadRoot(): ByteArray?
    fun loadTargets(): ByteArray?
    fun save(root: ByteArray, targets: ByteArray, versions: RepositoryVersions)
}

internal class PreferencesRepositoryStateStore(context: Context) : RepositoryStateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun loadVersions() = RepositoryVersions(
        root = preferences.getLong(ROOT_VERSION, 0),
        timestamp = preferences.getLong(TIMESTAMP_VERSION, 0),
        snapshot = preferences.getLong(SNAPSHOT_VERSION, 0),
        targets = preferences.getLong(TARGETS_VERSION, 0),
        trustedUntilEpochMillis = preferences.getLong(TRUSTED_UNTIL, 0),
    )

    override fun loadRoot(): ByteArray? = preferences.getString(ROOT_BYTES, null)
        ?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }

    override fun loadTargets(): ByteArray? = preferences.getString(TARGETS_BYTES, null)
        ?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }

    override fun save(root: ByteArray, targets: ByteArray, versions: RepositoryVersions) {
        val committed = preferences.edit()
            .putString(ROOT_BYTES, java.util.Base64.getEncoder().encodeToString(root))
            .putString(TARGETS_BYTES, java.util.Base64.getEncoder().encodeToString(targets))
            .putLong(ROOT_VERSION, versions.root)
            .putLong(TIMESTAMP_VERSION, versions.timestamp)
            .putLong(SNAPSHOT_VERSION, versions.snapshot)
            .putLong(TARGETS_VERSION, versions.targets)
            .putLong(TRUSTED_UNTIL, versions.trustedUntilEpochMillis)
            .commit()
        if (!committed) throw IOException("Unable to persist trusted repository state")
    }

    private companion object {
        const val PREFERENCES = "trusted_extension_repository"
        const val ROOT_BYTES = "root_bytes"
        const val TARGETS_BYTES = "targets_bytes"
        const val ROOT_VERSION = "root_version"
        const val TIMESTAMP_VERSION = "timestamp_version"
        const val SNAPSHOT_VERSION = "snapshot_version"
        const val TARGETS_VERSION = "targets_version"
        const val TRUSTED_UNTIL = "trusted_until_epoch_millis"
    }
}

/** A bounded TUF-style updater for the one code-owned official extension repository. */
internal class TrustedRepositoryClient(
    baseClient: OkHttpClient,
    private val embeddedRoot: ByteArray,
    private val stateStore: RepositoryStateStore,
    private val now: () -> Instant = Instant::now,
) {
    private val client = baseClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .proxy(Proxy.NO_PROXY)
        .cache(null)
        .build()
    private val mutex = Mutex()

    fun loadPersistedSnapshot(): RepositorySnapshot? {
        val targetsBytes = stateStore.loadTargets() ?: return null
        val versions = stateStore.loadVersions()
        if (versions.trustedUntilEpochMillis <= now().toEpochMilli()) {
            throw TrustedMetadataException("Persisted repository freshness window is missing or expired")
        }
        val rootBytes = stateStore.loadRoot() ?: embeddedRoot
        val verifier = TrustedMetadataVerifier(rootBytes, now)
        if (verifier.rootVersion != versions.root) {
            throw TrustedMetadataException("Persisted root version mismatch")
        }
        val targets = verifier.verify(targetsBytes, "targets")
        if (targets.version != versions.targets) {
            throw TrustedMetadataException("Persisted targets version mismatch")
        }
        return parseTargets(
            officialBaseUrl(OFFICIAL_BASE),
            targets.signed,
            verifier.rootVersion,
            targets.version,
            minOf(targets.expires.toEpochMilli(), versions.trustedUntilEpochMillis),
        )
    }

    suspend fun refresh(repoUrl: String): RepositorySnapshot = mutex.withLock {
        withContext(Dispatchers.IO) {
            val baseUrl = officialBaseUrl(repoUrl)
            val previous = stateStore.loadVersions()
            var trustedRootBytes = stateStore.loadRoot() ?: embeddedRoot
            var verifier = runCatching { TrustedMetadataVerifier(trustedRootBytes, now) }
                .getOrElse {
                    // Corrupt local state is not allowed to replace the embedded trust anchor.
                    trustedRootBytes = embeddedRoot
                    TrustedMetadataVerifier(embeddedRoot, now)
                }
            if (previous.root > verifier.rootVersion) {
                throw TrustedMetadataException("Persisted root version is ahead of trusted root")
            }

            for (rotation in 0 until MAX_ROOT_ROTATIONS_PER_REFRESH) {
                val nextVersion = verifier.rootVersion + 1
                val nextRoot = fetchOptional(baseUrl.resolve("metadata/$nextVersion.root.json")!!)
                    ?: break
                verifier.updateRoot(nextRoot)
                trustedRootBytes = nextRoot
            }

            val timestampBytes = fetchRequired(baseUrl.resolve("metadata/timestamp.json")!!)
            val timestamp = verifier.verify(timestampBytes, "timestamp")
            rejectRollback("timestamp", timestamp.version, previous.timestamp)

            val snapshotDescriptor = MetadataDescriptor.from(timestamp.signed, "snapshot.json")
            rejectRollback("snapshot descriptor", snapshotDescriptor.version, previous.snapshot)
            val snapshotBytes = fetchRequired(
                baseUrl.resolve("metadata/${snapshotDescriptor.version}.snapshot.json")!!,
            )
            snapshotDescriptor.verify(snapshotBytes, "snapshot")
            val snapshot = verifier.verify(snapshotBytes, "snapshot")
            requireDescriptorVersion("snapshot", snapshot.version, snapshotDescriptor.version)

            val targetsDescriptor = MetadataDescriptor.from(snapshot.signed, "targets.json")
            rejectRollback("targets descriptor", targetsDescriptor.version, previous.targets)
            val targetsBytes = fetchRequired(
                baseUrl.resolve("metadata/${targetsDescriptor.version}.targets.json")!!,
            )
            targetsDescriptor.verify(targetsBytes, "targets")
            val targets = verifier.verify(targetsBytes, "targets")
            requireDescriptorVersion("targets", targets.version, targetsDescriptor.version)
            rejectSameVersionReplacement(
                name = "targets",
                candidateVersion = targets.version,
                previousVersion = previous.targets,
                candidateBytes = targetsBytes,
                previousBytes = stateStore.loadTargets(),
            )

            val result = parseTargets(
                baseUrl,
                targets.signed,
                verifier.rootVersion,
                targets.version,
                minimumExpiryEpochMillis(
                    verifier.rootExpiresAtEpochMillis,
                    timestamp.expires.toEpochMilli(),
                    snapshot.expires.toEpochMilli(),
                    targets.expires.toEpochMilli(),
                ),
            )
            stateStore.save(
                trustedRootBytes,
                targetsBytes,
                RepositoryVersions(
                    root = verifier.rootVersion,
                    timestamp = timestamp.version,
                    snapshot = snapshot.version,
                    targets = targets.version,
                    trustedUntilEpochMillis = result.trust.expiresAtEpochMillis,
                ),
            )
            result
        }
    }

    suspend fun downloadAndVerify(context: Context, info: ExtensionInfo): File = withContext(Dispatchers.IO) {
        val expectedUrl = officialTargetUrl(info.apkUrl)
        val bytes = fetchRequired(expectedUrl, maxBytes = info.targetLength)
        if (bytes.size.toLong() != info.targetLength) throw TrustedMetadataException("APK length mismatch")
        if (!MessageDigest.isEqual(bytes.sha256(), info.sha256.hexBytes())) {
            throw TrustedMetadataException("APK hash mismatch")
        }
        val destination = File(context.cacheDir, "${info.sha256}.apk")
        destination.writeBytes(bytes)
        try {
            verifyArchiveIdentity(context.packageManager, destination, info)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
        destination
    }

    private fun parseTargets(
        baseUrl: HttpUrl,
        signed: JsonObject,
        rootVersion: Long,
        targetsVersion: Long,
        expiresAtEpochMillis: Long,
    ): RepositorySnapshot {
        val custom = signed.requiredObject("custom")
        val repositoryObject = custom.requiredObject("repository")
        val repository = RepoMetadata(
            name = repositoryObject.requiredString("name"),
            description = repositoryObject.requiredString("description"),
            baseUrl = OFFICIAL_BASE,
            iconUrl = repositoryObject.optionalString("iconUrl"),
            website = repositoryObject.optionalString("website"),
            signingKeyFingerprint = null,
        )
        val targets = signed.requiredObject("targets")
        if (targets.size() !in 1..MAX_TARGETS) throw TrustedMetadataException("Invalid target count")
        val extensions = targets.entrySet().map { (path, value) ->
            if (!path.matches(Regex("apk/[A-Za-z0-9._-]{1,200}\\.apk"))) {
                throw TrustedMetadataException("Unsafe APK target path")
            }
            val target = value.asJsonObject
            val metadata = target.requiredObject("custom")
            val packageName = metadata.requiredString("packageName")
            if (!packageName.matches(PACKAGE_PATTERN)) throw TrustedMetadataException("Invalid package name")
            val signerPins = metadata.requiredArray("apkSignerPins").mapTo(linkedSetOf()) {
                it.requiredStringValue("APK signer pin").lowercase(Locale.ROOT)
            }
            if (signerPins.isEmpty() || signerPins.any { !it.matches(SHA256_PATTERN) }) {
                throw TrustedMetadataException("Invalid APK signer policy")
            }
            val lineageRoot = metadata.requiredString("lineageRootSha256").lowercase(Locale.ROOT)
            if (!lineageRoot.matches(SHA256_PATTERN)) {
                throw TrustedMetadataException("Invalid APK signing lineage root")
            }
            val sources = metadata.requiredArray("sources").map { sourceElement ->
                val source = sourceElement.asJsonObject
                AvailableSource(
                    id = source.requiredString("id"),
                    name = source.optionalString("name") ?: source.requiredString("id"),
                    lang = source.optionalString("lang") ?: "und",
                    baseUrl = source.requiredString("baseUrl"),
                    serviceClass = source.requiredString("service"),
                    protocol = source.requiredPositiveLong("protocol").toInt(),
                    policyHash = source.requiredString("policyHash").lowercase(Locale.ROOT).also {
                        if (!it.matches(SHA256_PATTERN)) throw TrustedMetadataException("Invalid policy hash")
                    },
                )
            }
            if (sources.isEmpty()) throw TrustedMetadataException("Extension has no Sources")
            val length = target.requiredNonNegativeLong("length")
            if (length !in 1..MAX_APK_BYTES) throw TrustedMetadataException("APK size outside safety bounds")
            val sha256 = target.requiredObject("hashes").requiredString("sha256").lowercase(Locale.ROOT)
            if (!sha256.matches(SHA256_PATTERN)) throw TrustedMetadataException("Invalid APK hash")
            ExtensionInfo(
                id = packageName,
                name = metadata.requiredString("name"),
                version = metadata.requiredPositiveLong("versionCode"),
                versionName = metadata.requiredString("versionName"),
                language = metadata.optionalString("lang") ?: "und",
                iconUrl = null,
                apkUrl = baseUrl.resolve("targets/$path")!!.toString(),
                sha256 = sha256,
                targetLength = length,
                lineageRootSha256 = lineageRoot,
                signerPins = signerPins,
                sources = sources,
            )
        }
        if (extensions.map { it.id }.toSet().size != extensions.size) {
            throw TrustedMetadataException("Duplicate package in targets")
        }
        val allSourceIds = extensions.flatMap { extension -> extension.sources.map { it.id } }
        if (allSourceIds.toSet().size != allSourceIds.size) {
            throw TrustedMetadataException("Duplicate Source ownership in targets")
        }
        val signingPolicies = extensions.map { extension ->
            RepositorySigningPolicy(
                packageName = extension.id,
                expectedVersionCode = extension.version,
                targetLength = extension.targetLength,
                targetSha256 = extension.sha256,
                lineageAnchorsSha256 = setOf(extension.lineageRootSha256),
                approvedCurrentSignersSha256 = extension.signerPins,
                sources = extension.sources.associate { source ->
                    source.id to RepositorySourceService(
                        serviceClassName = source.serviceClass,
                        name = source.name,
                        lang = source.lang,
                        baseUrl = source.baseUrl,
                        protocol = source.protocol,
                        policyHash = source.policyHash,
                    )
                },
            )
        }
        return RepositorySnapshot(
            repository = repository,
            extensions = extensions.sortedBy { it.id },
            trust = VerifiedRepositoryTrustSnapshot(
                rootVersion = rootVersion,
                targetsVersion = targetsVersion,
                expiresAtEpochMillis = expiresAtEpochMillis,
                policies = signingPolicies,
            ),
        )
    }

    private fun fetchRequired(url: HttpUrl, maxBytes: Long = MAX_METADATA_BYTES): ByteArray =
        execute(url, maxBytes).use { response ->
            if (!response.isSuccessful) throw IOException("Fetch failed: HTTP ${response.code} for $url")
            response.body?.boundedBytes(maxBytes) ?: throw IOException("Empty response for $url")
        }

    private fun fetchOptional(url: HttpUrl): ByteArray? = execute(url, MAX_METADATA_BYTES).use { response ->
        when {
            response.code == 404 -> null
            !response.isSuccessful -> throw IOException("Fetch failed: HTTP ${response.code} for $url")
            else -> response.body?.boundedBytes(MAX_METADATA_BYTES)
                ?: throw IOException("Empty response for $url")
        }
    }

    private fun execute(url: HttpUrl, maxBytes: Long): Response {
        if (url.scheme != "https" || url.host != OFFICIAL_HOST || url.port != 443) {
            throw TrustedMetadataException("Repository URL escaped official origin")
        }
        return client.newCall(Request.Builder().url(url).get().build()).execute().also { response ->
            if (response.request.url != url) {
                response.close()
                throw TrustedMetadataException("Repository request URL changed")
            }
            val declaredLength = response.body?.contentLength() ?: -1
            if (declaredLength > maxBytes) {
                response.close()
                throw TrustedMetadataException("Repository response exceeds size limit")
            }
        }
    }

    private fun officialBaseUrl(value: String): HttpUrl {
        val normalized = value.trimEnd('/')
        val canonical = when (normalized) {
            OFFICIAL_WEB -> OFFICIAL_BASE
            OFFICIAL_BASE -> OFFICIAL_BASE
            else -> throw TrustedMetadataException("Only the official extension repository is trusted")
        }
        return "$canonical/".toHttpUrl()
    }

    private fun officialTargetUrl(value: String): HttpUrl {
        val url = runCatching { value.toHttpUrl() }
            .getOrElse { throw TrustedMetadataException("Invalid APK target URL", it) }
        if (url.scheme != "https" || url.host != OFFICIAL_HOST || url.port != 443 ||
            !url.encodedPath.startsWith("/komicaviewer/extensions/main/targets/apk/")
        ) {
            throw TrustedMetadataException("APK URL is outside signed target origin")
        }
        return url
    }

    private fun verifyArchiveIdentity(
        packageManager: PackageManager,
        archive: File,
        info: ExtensionInfo,
    ) {
        val packageInfo = packageManager.getPackageArchiveInfo(
            archive.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SERVICES or
                PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or PackageManager.GET_INSTRUMENTATION,
        ) ?: throw TrustedMetadataException("Downloaded target is not an APK")
        if (packageInfo.packageName != info.id || PackageInfoCompat.getLongVersionCode(packageInfo) != info.version) {
            throw TrustedMetadataException("APK package or version does not match targets metadata")
        }
        val signingInfo = packageInfo.signingInfo ?: throw TrustedMetadataException("APK has no signing info")
        if (signingInfo.hasMultipleSigners()) throw TrustedMetadataException("Multi-signer APK is forbidden")
        val current = signingInfo.apkContentsSigners.orEmpty().map { it.toByteArray().sha256Hex() }.toSet()
        if (current.size != 1 || current.single() !in info.signerPins) {
            throw TrustedMetadataException("APK signer is not authorized by targets metadata")
        }
        val history = signingInfo.signingCertificateHistory.orEmpty()
            .map { it.toByteArray().sha256Hex() }
            .toSet()
        if (info.lineageRootSha256 !in history || current.single() !in history) {
            throw TrustedMetadataException("APK signing lineage does not match targets metadata")
        }

        if (!packageInfo.requestedPermissions.isNullOrEmpty()) {
            throw TrustedMetadataException("Extension APK must not request Android permissions")
        }
        if (!packageInfo.activities.isNullOrEmpty() || !packageInfo.receivers.isNullOrEmpty() ||
            !packageInfo.providers.isNullOrEmpty() || !packageInfo.instrumentation.isNullOrEmpty()
        ) {
            throw TrustedMetadataException("Extension APK may contain only isolated Source services")
        }
        val expectedServices = info.sources.associateBy { it.serviceClass }
        val services = packageInfo.services.orEmpty()
        if (services.map { it.name }.toSet() != expectedServices.keys) {
            throw TrustedMetadataException("APK service set does not match targets metadata")
        }
        services.forEach { service ->
            val expected = expectedServices.getValue(service.name)
            val metadata = service.metaData
                ?: throw TrustedMetadataException("Source service has no metadata")
            if (!service.exported || service.permission != ExtensionProtocol.BIND_PERMISSION ||
                service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS == 0 ||
                service.flags and ServiceInfo.FLAG_EXTERNAL_SERVICE != 0 ||
                !service.processName.startsWith("${info.id}:") ||
                metadata.getInt(ExtensionProtocol.META_PROTOCOL, -1) != expected.protocol ||
                metadata.getString(ExtensionProtocol.META_SOURCE_ID) != expected.id ||
                metadata.getString(ExtensionProtocol.META_SOURCE_NAME) != expected.name ||
                metadata.getString(ExtensionProtocol.META_SOURCE_LANG) != expected.lang ||
                metadata.getString(ExtensionProtocol.META_SOURCE_BASE_URL) != expected.baseUrl
            ) {
                throw TrustedMetadataException("Source service contract does not match targets metadata")
            }
        }
        if (services.map { it.processName }.toSet().size != services.size) {
            throw TrustedMetadataException("Every Source service must use a distinct private process")
        }
    }

    private fun rejectRollback(name: String, candidate: Long, previous: Long) {
        if (candidate < previous) throw TrustedMetadataException("$name rollback detected")
    }

    private fun requireDescriptorVersion(name: String, actual: Long, expected: Long) {
        if (actual != expected) throw TrustedMetadataException("$name version mix-and-match detected")
    }

    private companion object {
        const val OFFICIAL_WEB = "https://github.com/komicaviewer/extensions"
        const val OFFICIAL_BASE = "https://raw.githubusercontent.com/komicaviewer/extensions/main"
        const val OFFICIAL_HOST = "raw.githubusercontent.com"
        const val MAX_ROOT_ROTATIONS_PER_REFRESH = 32
        const val MAX_TARGETS = 64
        const val MAX_METADATA_BYTES = 4L * 1024 * 1024
        const val MAX_APK_BYTES = 64L * 1024 * 1024
        val PACKAGE_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
    }
}

private fun okhttp3.ResponseBody.boundedBytes(limit: Long): ByteArray {
    byteStream().use { input ->
        val output = ByteArrayOutputStream(minOf(limit, 8192).toInt())
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw TrustedMetadataException("Repository response exceeds size limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

private fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)
private fun ByteArray.sha256Hex(): String = sha256().joinToString("") { "%02x".format(it) }
private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun rejectSameVersionReplacement(
    name: String,
    candidateVersion: Long,
    previousVersion: Long,
    candidateBytes: ByteArray,
    previousBytes: ByteArray?,
) {
    if (candidateVersion == previousVersion && previousBytes != null &&
        !MessageDigest.isEqual(candidateBytes.sha256(), previousBytes.sha256())
    ) {
        throw TrustedMetadataException("$name changed without a version increment")
    }
}

internal fun minimumExpiryEpochMillis(vararg expiries: Long): Long {
    require(expiries.isNotEmpty() && expiries.all { it > 0 })
    return expiries.minOrNull()!!
}

private fun JsonObject.optionalString(name: String): String? = get(name)?.takeUnless { it.isJsonNull }
    ?.asJsonPrimitive?.takeIf { it.isString }?.asString?.trim()?.takeIf(String::isNotEmpty)
