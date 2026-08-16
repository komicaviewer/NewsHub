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
    fun publicRecurringProfileKeepsAllThirteenAndUsesOperationLevelAuthentication() {
        val full = officialProfile()
        val selection = ExtensionHealthProfileSelection.selectionFor(
            ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE,
        )
        val public = selection.select(full)

        assertEquals(13, public.sources.size)
        assertEquals(0, public.sources.count { it.requireAuthenticatedSession })
        assertEquals(
            setOf(
                "tw.kevinzhang.eyny",
                "tw.kevinzhang.newshub.extension.gamer",
            ),
            public.sources.filter { it.authenticatedOperations.isNotEmpty() }.mapTo(linkedSetOf()) { it.sourceId },
        )
        assertEquals(
            setOf(
                HealthProbeOperation.GET_THREAD_SUMMARIES,
                HealthProbeOperation.GET_THREAD_PAGE,
            ),
            public.sources.first { it.sourceId == "tw.kevinzhang.eyny" }.authenticatedOperations,
        )
        assertEquals(35, public.maxRequests)
        assertEquals(ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE, public.profileId)
        assertEquals(true, selection.allowAuthPending)
    }

    @Test
    fun fullProfileRetainsCredentialValidationAndKomica2SoraExactHosts() {
        val full = ExtensionHealthProfileSelection.selectionFor(null).select(officialProfile())

        assertEquals(2, full.sources.count { it.requireAuthenticatedSession })
        assertEquals(41, full.maxRequests)
        assertEquals(
            setOf("2cat.org", "2cat.uk"),
            full.sources.first { it.sourceId == "tw.kevinzhang.komica2.sora" }.allowedHosts,
        )
    }

    @Test
    fun profileRejectsAnAuthenticatedBoardDirectoryOrUnsafeDependencyGap() {
        val source = officialProfile().sources.first()

        assertThrows(IllegalArgumentException::class.java) {
            source.copy(
                authenticatedOperations = setOf(HealthProbeOperation.GET_BOARD_PAGE),
            ).validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            source.copy(
                authenticatedOperations = setOf(HealthProbeOperation.GET_THREAD_SUMMARIES),
            ).validate()
        }
    }

    private fun resource(name: String): String = requireNotNull(
        javaClass.classLoader?.getResource("extension-health/$name"),
    ).readText()

    private fun officialProfile(): ExtensionHealthProfile = ExtensionHealthJson.decodeProfile(
        java.io.File("src/androidTest/assets/extension-health/profile-v1.json").readText(),
    )
}
