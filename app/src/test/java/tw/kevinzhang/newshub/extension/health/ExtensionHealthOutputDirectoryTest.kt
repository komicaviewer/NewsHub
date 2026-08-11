package tw.kevinzhang.newshub.extension.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExtensionHealthOutputDirectoryTest {
    @Test
    fun nullArgumentPreservesDefaultDirectory() {
        val defaultDirectory = File("build/extension-health")

        assertEquals(defaultDirectory, ExtensionHealthOutputDirectory.resolve(null, defaultDirectory))
    }

    @Test
    fun acceptsOnlyNormalizedDescendantsOfFirebasePullRoots() {
        listOf(
            "/sdcard/Download/newshub-extension-health",
            "/storage/emulated/0/Download/newshub_extension-health+1",
            "/data/local/tmp/newshub.extension-health",
        ).forEach { path ->
            val resolved = ExtensionHealthOutputDirectory.resolve(path, File("unused"))
            assertTrue(resolved.isAbsolute)
        }
    }

    @Test
    fun rejectsTraversalShellSyntaxBroadRootsAndRelativePaths() {
        listOf(
            "/sdcard/Download/../secrets",
            "/sdcard//Download/health",
            "/sdcard/Download/health/",
            "/sdcard/Download/health;id",
            "/storage/health path",
            "/data/local/tmp/health$(id)",
            "/data/local/tmp/health\\escape",
            "/sdcard",
            "/storage",
            "/data/local/tmp",
            "/data/newshub-health",
            "sdcard/Download/newshub-health",
        ).forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) {
                ExtensionHealthOutputDirectory.resolve(path, File("unused"))
            }
        }
    }

    @Test
    fun rejectsNestedExportThatCouldRecursivelyCopyStaging() {
        val staging = File("/storage/emulated/0/Android/data/app/files/extension-health")

        assertThrows(IllegalArgumentException::class.java) {
            ExtensionHealthOutputDirectory.requireNonOverlapping(
                staging,
                File(staging, "firebase-export"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionHealthOutputDirectory.requireNonOverlapping(
                staging,
                requireNotNull(staging.parentFile),
            )
        }
        ExtensionHealthOutputDirectory.requireNonOverlapping(staging, staging)
    }
}
