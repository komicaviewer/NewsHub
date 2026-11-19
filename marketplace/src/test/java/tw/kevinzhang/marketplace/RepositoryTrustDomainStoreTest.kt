package tw.kevinzhang.marketplace

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RepositoryTrustDomainStoreTest {
    @Test
    fun `bootstrap root persists independently and uncommitted onboarding can be removed`() {
        val root = createTempDir(prefix = "trust-bootstrap-")
        val domain = "33333333-3333-4333-8333-333333333333"
        val store = RepositoryTrustDomainStore(root, domain)

        store.saveBootstrapRoot(bytes("bootstrap"))
        assertArrayEquals(bytes("bootstrap"), RepositoryTrustDomainStore(root, domain).loadBootstrapRoot())
        assertNull(store.loadTargets())

        store.deleteUncommittedDomain()
        assertFalse(File(root, domain).exists())
    }
    @Test
    fun `two repository domains keep independent generations`() {
        val root = Files.createTempDirectory("repository-domains").toFile()
        val domainA = "11111111-1111-4111-8111-111111111111"
        val domainB = "22222222-2222-4222-8222-222222222222"
        val a = RepositoryTrustDomainStore(root, domainA)
        val b = RepositoryTrustDomainStore(root, domainB)

        a.save(bytes("root-a"), bytes("timestamp-a"), bytes("snapshot-a"), bytes("targets-a"), versions(5))
        b.save(bytes("root-b"), bytes("timestamp-b"), bytes("snapshot-b"), bytes("targets-b"), versions(2))

        assertArrayEquals(bytes("root-a"), RepositoryTrustDomainStore(root, domainA).loadRoot())
        assertArrayEquals(bytes("targets-b"), RepositoryTrustDomainStore(root, domainB).loadTargets())
        assertEquals(5L, a.loadVersions().root)
        assertEquals(2L, b.loadVersions().root)
        assertFalse(a.loadVersions().trustedUntilEpochMillis == b.loadVersions().trustedUntilEpochMillis)
    }

    @Test
    fun `interruption before CURRENT commit preserves prior complete generation`() {
        val root = Files.createTempDirectory("repository-interruption").toFile()
        val domain = "33333333-3333-4333-8333-333333333333"
        RepositoryTrustDomainStore(root, domain).save(
            bytes("root-1"), bytes("timestamp-1"), bytes("snapshot-1"), bytes("targets-1"), versions(1),
        )
        val interrupted = RepositoryTrustDomainStore(root, domain) { error("simulated interruption") }

        assertThrows(IllegalStateException::class.java) {
            interrupted.save(
                bytes("root-2"), bytes("timestamp-2"), bytes("snapshot-2"), bytes("targets-2"), versions(2),
            )
        }

        val recovered = RepositoryTrustDomainStore(root, domain)
        assertEquals(1L, recovered.loadVersions().root)
        assertArrayEquals(bytes("targets-1"), recovered.loadTargets())
    }

    @Test
    fun `changing bootstrap root discards persisted generations`() {
        val root = Files.createTempDirectory("repository-root-change").toFile()
        val domain = "44444444-4444-4444-8444-444444444444"
        val store = RepositoryTrustDomainStore(root, domain)
        store.saveBootstrapRoot(bytes("root-a"))
        store.save(bytes("root-a"), bytes("timestamp-a"), bytes("snapshot-a"), bytes("targets-a"), versions(7))

        store.saveBootstrapRoot(bytes("root-b"))

        val reopened = RepositoryTrustDomainStore(root, domain)
        assertNull(reopened.loadRoot())
        assertNull(reopened.loadTargets())
        assertEquals(RepositoryVersions(), reopened.loadVersions())
        assertArrayEquals(bytes("root-b"), reopened.loadBootstrapRoot())
    }

    @Test
    fun `changing bootstrap root leaves other domains untouched`() {
        val root = Files.createTempDirectory("repository-root-change-isolated").toFile()
        val official = RepositoryTrustDomainStore(root, "44444444-4444-4444-8444-444444444444")
        val other = RepositoryTrustDomainStore(root, "55555555-5555-4555-8555-555555555555")
        official.saveBootstrapRoot(bytes("root-a"))
        other.saveBootstrapRoot(bytes("root-x"))
        other.save(bytes("root-x"), bytes("timestamp-x"), bytes("snapshot-x"), bytes("targets-x"), versions(3))

        official.saveBootstrapRoot(bytes("root-b"))

        val reopened = RepositoryTrustDomainStore(root, "55555555-5555-4555-8555-555555555555")
        assertArrayEquals(bytes("targets-x"), reopened.loadTargets())
        assertArrayEquals(bytes("root-x"), reopened.loadBootstrapRoot())
    }

    @Test
    fun `same bootstrap root keeps generations`() {
        val root = Files.createTempDirectory("repository-root-same").toFile()
        val domain = "44444444-4444-4444-8444-444444444444"
        val store = RepositoryTrustDomainStore(root, domain)
        store.saveBootstrapRoot(bytes("root-a"))
        store.save(bytes("root-a"), bytes("timestamp-a"), bytes("snapshot-a"), bytes("targets-a"), versions(7))

        store.saveBootstrapRoot(bytes("root-a"))

        val reopened = RepositoryTrustDomainStore(root, domain)
        assertArrayEquals(bytes("targets-a"), reopened.loadTargets())
        assertEquals(7L, reopened.loadVersions().root)
    }

    @Test
    fun `first bootstrap root keeps pre-existing generations`() {
        val root = Files.createTempDirectory("repository-root-first").toFile()
        val domain = "44444444-4444-4444-8444-444444444444"
        val store = RepositoryTrustDomainStore(root, domain)
        store.save(bytes("root-a"), bytes("timestamp-a"), bytes("snapshot-a"), bytes("targets-a"), versions(7))

        store.saveBootstrapRoot(bytes("root-a"))

        assertArrayEquals(bytes("targets-a"), RepositoryTrustDomainStore(root, domain).loadTargets())
    }

    private fun versions(version: Long) = RepositoryVersions(
        root = version,
        timestamp = version,
        snapshot = version,
        targets = version,
        trustedUntilEpochMillis = version * 1_000,
    )

    private fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
}
