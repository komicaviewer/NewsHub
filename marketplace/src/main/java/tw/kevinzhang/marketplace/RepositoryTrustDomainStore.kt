package tw.kevinzhang.marketplace

import androidx.core.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Per-domain immutable metadata generations. Blobs are durable before CURRENT is atomically
 * replaced, so an interrupted refresh can only expose the previous complete generation.
 */
internal class RepositoryTrustDomainStore(
    baseDirectory: File,
    domainId: String,
    private val beforeCurrentCommit: () -> Unit = {},
) : RepositoryStateStore {
    private val domainDirectory = File(baseDirectory, domainId)
    private val blobDirectory = File(domainDirectory, "blobs")
    private val currentFile = AtomicFile(File(domainDirectory, "CURRENT.json"))
    private val bootstrapRootFile = AtomicFile(File(domainDirectory, "BOOTSTRAP_ROOT.json"))

    init {
        if (runCatching { UUID.fromString(domainId).toString() == domainId }.getOrDefault(false).not()) {
            throw IllegalArgumentException("Repository domain id must be a canonical UUID")
        }
        if (!blobDirectory.mkdirs() && !blobDirectory.isDirectory) {
            throw IOException("Unable to create repository trust directory")
        }
    }

    override fun loadVersions(): RepositoryVersions = loadGeneration()?.versions ?: RepositoryVersions()

    override fun loadRoot(): ByteArray? = loadGeneration()?.root

    override fun loadTargets(): ByteArray? = loadGeneration()?.targets

    fun loadBootstrapRoot(): ByteArray? = bootstrapRootFile.baseFile
        .takeIf(File::isFile)
        ?.let { bootstrapRootFile.openRead().use { input -> input.readBytes() } }

    fun saveBootstrapRoot(root: ByteArray) {
        if (loadBootstrapRoot()?.contentEquals(root) == true) return
        var output: FileOutputStream? = null
        try {
            output = bootstrapRootFile.startWrite()
            output.write(root)
            output.fd.sync()
            bootstrapRootFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(bootstrapRootFile::failWrite)
            throw error
        }
    }

    /** Used only to roll back an onboarding attempt that never became a persisted domain. */
    fun deleteUncommittedDomain() {
        domainDirectory.deleteRecursively()
    }

    override fun save(
        root: ByteArray,
        timestamp: ByteArray,
        snapshot: ByteArray,
        targets: ByteArray,
        versions: RepositoryVersions,
    ) {
        val hashes = linkedMapOf(
            "root" to writeImmutableBlob(root),
            "timestamp" to writeImmutableBlob(timestamp),
            "snapshot" to writeImmutableBlob(snapshot),
            "targets" to writeImmutableBlob(targets),
        )
        val manifest = JsonObject().apply {
            addProperty("schemaVersion", 1)
            add("hashes", JsonObject().apply { hashes.forEach(::addProperty) })
            add("versions", JsonObject().apply {
                addProperty("root", versions.root)
                addProperty("timestamp", versions.timestamp)
                addProperty("snapshot", versions.snapshot)
                addProperty("targets", versions.targets)
                addProperty("trustedUntilEpochMillis", versions.trustedUntilEpochMillis)
            })
        }.toString().toByteArray(Charsets.UTF_8)

        beforeCurrentCommit()
        var output: FileOutputStream? = null
        try {
            output = currentFile.startWrite()
            output.write(manifest)
            output.fd.sync()
            currentFile.finishWrite(output)
        } catch (error: Exception) {
            output?.let(currentFile::failWrite)
            throw error
        }
    }

    private fun loadGeneration(): StoredGeneration? {
        if (!currentFile.baseFile.isFile) return null
        val manifest = runCatching {
            currentFile.openRead().use { JsonParser.parseReader(it.reader()).asJsonObject }
        }.getOrElse { throw TrustedMetadataException("Invalid repository CURRENT generation", it) }
        if (manifest.get("schemaVersion")?.asInt != 1) {
            throw TrustedMetadataException("Unsupported repository generation schema")
        }
        val hashes = manifest.getAsJsonObject("hashes")
            ?: throw TrustedMetadataException("Repository generation has no blobs")
        fun blob(role: String): ByteArray {
            val hash = hashes.get(role)?.asString.orEmpty()
            if (!hash.matches(SHA256_PATTERN)) throw TrustedMetadataException("Invalid $role blob hash")
            val bytes = File(blobDirectory, "$hash.json").takeIf(File::isFile)?.readBytes()
                ?: throw TrustedMetadataException("Missing $role blob")
            if (bytes.sha256Hex() != hash) throw TrustedMetadataException("Corrupt $role blob")
            return bytes
        }
        val versionObject = manifest.getAsJsonObject("versions")
            ?: throw TrustedMetadataException("Repository generation has no versions")
        fun positiveOrZero(name: String): Long = versionObject.get(name)?.asLong?.also {
            if (it < 0) throw TrustedMetadataException("Invalid $name version")
        } ?: throw TrustedMetadataException("Missing $name version")
        return StoredGeneration(
            root = blob("root"),
            timestamp = blob("timestamp"),
            snapshot = blob("snapshot"),
            targets = blob("targets"),
            versions = RepositoryVersions(
                root = positiveOrZero("root"),
                timestamp = positiveOrZero("timestamp"),
                snapshot = positiveOrZero("snapshot"),
                targets = positiveOrZero("targets"),
                trustedUntilEpochMillis = positiveOrZero("trustedUntilEpochMillis"),
            ),
        )
    }

    private fun writeImmutableBlob(bytes: ByteArray): String {
        val hash = bytes.sha256Hex()
        val destination = File(blobDirectory, "$hash.json")
        if (destination.isFile) {
            if (destination.readBytes().sha256Hex() != hash) {
                throw TrustedMetadataException("Immutable metadata blob is corrupt")
            }
            return hash
        }
        val temporary = File.createTempFile("metadata-", ".tmp", blobDirectory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination) && !destination.isFile) {
                throw IOException("Unable to publish immutable metadata blob")
            }
        } finally {
            temporary.delete()
        }
        return hash
    }

    private data class StoredGeneration(
        val root: ByteArray,
        val timestamp: ByteArray,
        val snapshot: ByteArray,
        val targets: ByteArray,
        val versions: RepositoryVersions,
    )

    private companion object {
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
    }
}

private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }
