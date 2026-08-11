package tw.kevinzhang.newshub.extension.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtensionHealthProfileSelectionTest {
    @Test
    fun defaultRemainsTheFullOfficialProfile() {
        val selection = ExtensionHealthProfileSelection.selectionFor(null)

        assertEquals("extension-health/profile-v1.json", selection.assetPath)
        assertFalse(selection.allowAuthPending)
    }

    @Test
    fun zeroSecretProfileContainsOnlyHackerNewsAndThreeRequests() {
        val profile = ExtensionHealthJson.decodeProfile(resource("profile-hackernews-v1.json"))

        assertEquals(ExtensionHealthProfileSelection.ZERO_SECRET_HACKERNEWS_PROFILE, profile.profileId)
        assertEquals(3, profile.maxRequests)
        assertEquals(120_000L, profile.runTimeoutMs)
        assertEquals(
            listOf("tw.kevinzhang.newshub.extension.hackernews"),
            profile.sources.map(SourceHealthProfile::sourceId),
        )
        assertFalse(profile.sources.single().requireAuthenticatedSession)
    }

    @Test
    fun arbitraryAssetPathsCannotBeSelected() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionHealthProfileSelection.assetFor("../../private/credentials.json")
        }
    }

    @Test
    fun candidateClosedCatalogSelectsExactBundleSources() {
        val full = officialProfile()
        val komica = ExtensionHealthProfileSelection.selectionFor("candidate-komica-v1").select(full)

        assertEquals("candidate-komica-v1", komica.profileId)
        assertEquals(5, komica.sources.size)
        assertEquals(setOf("tw.kevinzhang.newshub.extension.komica"), komica.sources.map { it.packageName }.toSet())
        assertEquals(15, komica.maxRequests)
    }

    @Test
    fun publicRecurringProfileKeepsAllThirteenButAllowsTwoPendingSessions() {
        val full = officialProfile()
        val selection = ExtensionHealthProfileSelection.selectionFor(
            ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE,
        )
        val public = selection.select(full)

        assertEquals(13, public.sources.size)
        assertEquals(2, public.sources.count { it.requireAuthenticatedSession })
        assertEquals(ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE, public.profileId)
        assertEquals(true, selection.allowAuthPending)
    }

    private fun resource(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("extension-health/$name"),
    ).readText()

    private fun officialProfile(): ExtensionHealthProfile = ExtensionHealthJson.decodeProfile(
        java.io.File("src/androidTest/assets/extension-health/profile-v1.json").readText(),
    )
}
