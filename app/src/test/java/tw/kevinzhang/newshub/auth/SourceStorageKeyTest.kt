package tw.kevinzhang.newshub.auth

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.SourceIdentity

class SourceStorageKeyTest {
    @Test
    fun `same extension identity in different repository domains has isolated storage`() {
        val official = SourceIdentity(
            packageName = "example.extension",
            signerSha256 = "a".repeat(64),
            sourceId = "example.source",
            repositoryDomainId = "00000000-0000-0000-0000-000000000001",
        )
        val thirdParty = official.copy(
            repositoryDomainId = "00000000-0000-0000-0000-000000000002",
        )

        assertNotEquals(sourceStorageKey(official), sourceStorageKey(thirdParty))
        assertEquals(sourceStorageKey(official), sourceStorageKey(official.copy()))
    }
}
