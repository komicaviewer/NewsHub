package tw.kevinzhang.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SavedPostAssetStoreTest {
    private lateinit var sandbox: File
    private lateinit var root: File
    private lateinit var store: SavedPostAssetStore

    @Before
    fun setUp() {
        sandbox = Files.createTempDirectory("saved-post-assets").toFile()
        root = File(sandbox, "managed")
        store = SavedPostAssetStore(root)
    }

    @After
    fun tearDown() {
        sandbox.deleteRecursively()
    }

    @Test
    fun `untrusted identifiers cannot escape the managed root`() {
        val directory = store.threadDirectory("../../outside", "/tmp/thread")

        assertTrue(directory.canonicalPath.startsWith(root.canonicalPath + File.separator))
        assertFalse(directory.path.contains(".."))
        assertNotEquals(File(sandbox, "outside").canonicalPath, directory.canonicalPath)
    }

    @Test
    fun `absolute traversal and malformed references are rejected`() {
        val outside = File(sandbox, "sentinel").apply { writeText("keep") }

        assertNull(store.resolve("../sentinel"))
        assertNull(store.resolve(outside.absolutePath))
        assertNull(store.resolve(""))
        store.deleteSerializedReferences("[\"../sentinel\",\"${outside.absolutePath}\"]")

        assertEquals("keep", outside.readText())
    }

    @Test
    fun `managed-looking symlink cannot escape the root`() {
        val outsideDirectory = File(sandbox, "outside").apply { mkdirs() }
        val outside = File(outsideDirectory, "post_0.png").apply { writeText("keep") }
        val managedDirectory = store.threadDirectory("source", "thread")
        val reference = "${managedDirectory.name}/post_0.png"
        managedDirectory.delete()
        Files.createSymbolicLink(managedDirectory.toPath(), outsideDirectory.toPath())

        assertNull(store.resolve(reference))
        store.deleteReferences(listOf(reference))
        assertEquals("keep", outside.readText())
    }

    @Test
    fun `legacy root cleanup does not follow symlinks`() {
        val outsideDirectory = File(sandbox, "outside-legacy").apply { mkdirs() }
        val outside = File(outsideDirectory, "sentinel").apply { writeText("keep") }
        val legacy = File(root, "source_thread").apply { mkdirs() }
        File(legacy, "post_0.png").writeText("legacy")
        val symlink = File(root, "legacy-link")
        Files.createSymbolicLink(symlink.toPath(), outsideDirectory.toPath())

        store.threadDirectory("source", "thread")

        assertFalse(legacy.exists())
        assertFalse(Files.exists(symlink.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("keep", outside.readText())
    }

    @Test
    fun `managed asset round trips as an opaque relative reference`() {
        val directory = store.threadDirectory("source", "thread")
        val asset = File(directory, "post_0.png").apply { writeText("image") }

        val reference = store.referenceFor(asset)

        assertTrue(reference.matches(Regex("[0-9a-f]{64}/post_0\\.png")))
        assertEquals(asset.canonicalFile, store.resolve(reference)?.canonicalFile)
        store.deleteReferences(listOf(reference))
        assertFalse(asset.exists())
    }

    @Test
    fun `source and thread boundaries cannot collide`() {
        val first = store.threadDirectory("ab", "c").name
        val second = store.threadDirectory("a", "bc").name

        assertNotEquals(first, second)
    }
}
