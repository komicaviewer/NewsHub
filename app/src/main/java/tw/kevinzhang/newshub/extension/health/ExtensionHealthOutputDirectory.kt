package tw.kevinzhang.newshub.extension.health

import java.io.File

/** Validates the non-sensitive artifact destination accepted from instrumentation arguments. */
object ExtensionHealthOutputDirectory {
    private const val MAX_PATH_LENGTH = 240
    private val SAFE_PATH = Regex("/[A-Za-z0-9._+/\\-]+")
    private val ALLOWED_ROOTS = listOf(
        File("/sdcard"),
        File("/storage"),
        File("/data/local/tmp"),
    )

    /** A null argument preserves the app-owned external-files directory used outside Test Lab. */
    fun resolve(argument: String?, defaultDirectory: File): File {
        if (argument == null) return defaultDirectory
        require(argument.isNotBlank() && argument.length <= MAX_PATH_LENGTH) {
            "Invalid extension health output root"
        }
        require(argument == argument.trim() && argument.matches(SAFE_PATH)) {
            "Invalid extension health output root"
        }
        val lexicalFile = File(argument)
        require(lexicalFile.isAbsolute && lexicalFile.toPath().normalize().toString() == argument) {
            "Extension health output root must be a normalized absolute path"
        }
        require(ALLOWED_ROOTS.any { root -> lexicalFile.isStrictDescendantOf(root) }) {
            "Extension health output root is outside Firebase Test Lab pull roots"
        }

        val canonicalFile = lexicalFile.safeCanonicalFile()
        require(ALLOWED_ROOTS.map(File::safeCanonicalFile).any { root ->
            canonicalFile.isStrictDescendantOf(root)
        }) {
            "Extension health output root escapes its allowed root"
        }
        return canonicalFile
    }

    /** Copying a directory into itself or one of its ancestors/descendants is forbidden. */
    fun requireNonOverlapping(stagingDirectory: File, exportDirectory: File) {
        val staging = stagingDirectory.safeCanonicalFile()
        val export = exportDirectory.safeCanonicalFile()
        if (staging == export) return
        require(!staging.isStrictDescendantOf(export) && !export.isStrictDescendantOf(staging)) {
            "Extension health staging and export directories overlap"
        }
    }

    fun isShellSafe(directory: File): Boolean =
        directory.path.length <= MAX_PATH_LENGTH && directory.path.matches(SAFE_PATH)
}

private fun File.isStrictDescendantOf(root: File): Boolean =
    path.startsWith(root.path.trimEnd(File.separatorChar) + File.separator)

private fun File.safeCanonicalFile(): File = runCatching { canonicalFile }
    .getOrElse { throw IllegalArgumentException("Unable to resolve extension health output root") }
