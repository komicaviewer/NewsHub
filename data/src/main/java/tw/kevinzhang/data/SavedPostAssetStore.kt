package tw.kevinzhang.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the only filesystem namespace used by saved-post assets.
 *
 * Source-provided identifiers and database strings are untrusted. They are never interpolated
 * into a path, and every persisted reference is revalidated against the canonical managed root.
 */
@Singleton
class SavedPostAssetStore internal constructor(
    private val rootDirectory: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, ROOT_NAME))

    private val gson = Gson()

    fun threadDirectory(sourceId: String, threadId: String): File {
        val root = managedRoot()
        val directory = File(root, opaqueThreadKey(sourceId, threadId)).canonicalFile
        check(isDescendant(root, directory)) { "Managed asset directory escaped its root" }
        check(directory.mkdirs() || directory.isDirectory) { "Unable to create saved-post asset directory" }
        return directory
    }

    fun referenceFor(file: File): String {
        val root = managedRoot()
        val canonicalFile = file.canonicalFile
        require(canonicalFile.isFile) { "Saved-post asset must be an existing regular file" }
        require(isDescendant(root, canonicalFile)) { "Saved-post asset is outside the managed root" }

        val reference = canonicalFile.relativeTo(root).invariantSeparatorsPath
        require(isValidReference(reference)) { "Saved-post asset has an invalid managed name" }
        return reference
    }

    fun resolve(reference: String): File? {
        if (!isValidReference(reference)) return null
        val root = managedRoot()
        val candidate = File(root, reference).canonicalFile
        return candidate.takeIf { isDescendant(root, it) && it.isFile }
    }

    fun encodeReferences(references: List<String>): String {
        require(references.all(::isValidReference)) { "Invalid saved-post asset reference" }
        return gson.toJson(references.distinct())
    }

    fun decodeReferences(serialized: String): List<String> {
        return try {
            val json = JsonParser.parseString(serialized)
            if (!json.isJsonArray) return emptyList()
            json.asJsonArray.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }.filter(::isValidReference).distinct()
        } catch (_: JsonSyntaxException) {
            emptyList()
        } catch (_: ClassCastException) {
            emptyList()
        }
    }

    fun resolveReferences(serialized: String): List<File> =
        decodeReferences(serialized).mapNotNull(::resolve)

    fun deleteReferences(references: List<String>) {
        references.distinct().forEach { reference ->
            val asset = resolve(reference) ?: return@forEach
            val parent = asset.parentFile
            asset.delete()
            if (parent != null && parent.list()?.isEmpty() == true) parent.delete()
        }
    }

    fun deleteSerializedReferences(serialized: String) {
        deleteReferences(decodeReferences(serialized))
    }

    private fun managedRoot(): File {
        val root = rootDirectory.canonicalFile
        check(root.mkdirs() || root.isDirectory) { "Unable to create saved-post asset root" }
        migrateLegacyRoot(root)
        return root
    }

    /** One-time v7 cleanup. The file visitor never follows symlinks outside the managed root. */
    private fun migrateLegacyRoot(root: File) {
        synchronized(MIGRATION_LOCK) {
            val marker = File(root, MIGRATION_MARKER)
            if (marker.isFile) return

            Files.newDirectoryStream(root.toPath()).use { entries ->
                entries.filter { it.fileName.toString() != MIGRATION_MARKER }.forEach(::deleteTreeNoFollow)
            }
            check(marker.createNewFile() || marker.isFile) { "Unable to mark saved-post asset migration" }
        }
    }

    private fun deleteTreeNoFollow(path: Path) {
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun opaqueThreadKey(sourceId: String, threadId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(KEY_DOMAIN.toByteArray(StandardCharsets.UTF_8))
        digest.updateLengthPrefixed(sourceId)
        digest.updateLengthPrefixed(threadId)
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun isValidReference(reference: String): Boolean {
        if (reference.isBlank() || File(reference).isAbsolute || '\\' in reference) return false
        if (reference.split('/').any { it == "." || it == ".." || it.isBlank() }) return false
        return REFERENCE_PATTERN.matches(reference)
    }

    private fun isDescendant(root: File, candidate: File): Boolean =
        candidate.path.startsWith(root.path + File.separator)

    private fun MessageDigest.updateLengthPrefixed(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        update(byteArrayOf(
            (bytes.size ushr 24).toByte(),
            (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),
            bytes.size.toByte(),
        ))
        update(bytes)
    }

    companion object {
        private const val ROOT_NAME = "saved_posts"
        private const val KEY_DOMAIN = "newshub-saved-post-assets-v1"
        private const val MIGRATION_MARKER = ".opaque-assets-v1"
        private val REFERENCE_PATTERN = Regex("[0-9a-f]{64}/post_[0-9]+\\.png")
        private val MIGRATION_LOCK = Any()

        fun forAppFilesDirectory(filesDirectory: File): SavedPostAssetStore =
            SavedPostAssetStore(File(filesDirectory, ROOT_NAME))
    }
}
