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
    fun twocatCandidatesSelectOnlyTheirSourceAndLimitRequestsToThree() {
        val full = officialProfile()
        listOf("komica", "komica2").forEach { bundle ->
            val profileId = "candidate-$bundle-twocat-v1"
            val selection = ExtensionHealthProfileSelection.selectionFor(profileId)
            val selected = selection.select(full)

            assertEquals(profileId, selected.profileId)
            assertEquals("extension-health/profile-v1.json", selection.assetPath)
            assertEquals(listOf("tw.kevinzhang.$bundle.twocat"), selected.sources.map { it.sourceId })
            assertEquals("tw.kevinzhang.newshub.extension.$bundle", selected.sources.single().packageName)
            assertEquals(3, selected.maxRequests)
            assertFalse(selected.sources.single().requireAuthenticatedSession)
            assertFalse(selection.allowAuthPending)
        }
    }

    @Test
    fun twocatCandidatesRejectMissingSourcesAndUnexpectedPackages() {
        val full = officialProfile()
        listOf("komica", "komica2").forEach { bundle ->
            val selection = ExtensionHealthProfileSelection.selectionFor("candidate-$bundle-twocat-v1")
            val sourceId = "tw.kevinzhang.$bundle.twocat"

            assertThrows(IllegalArgumentException::class.java) {
                selection.select(full.copy(sources = full.sources.filterNot { it.sourceId == sourceId }))
            }
            assertThrows(IllegalArgumentException::class.java) {
                selection.select(full.copy(sources = full.sources.map { source ->
                    if (source.sourceId == sourceId) source.copy(packageName = "unexpected.extension") else source
                }))
            }
        }
    }

    @Test
    fun publicRecurringProfileNarrowsToRequestedSourcesAndRecomputesRequestBudget() {
        val selection = ExtensionHealthProfileSelection.selectionFor(
            ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE,
            "tw.kevinzhang.mobile01, tw.kevinzhang.eyny,tw.kevinzhang.mobile01",
        )
        val narrowed = selection.select(officialProfile())

        assertEquals(ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE, narrowed.profileId)
        assertEquals(
            setOf("tw.kevinzhang.mobile01", "tw.kevinzhang.eyny"),
            narrowed.sources.mapTo(linkedSetOf()) { it.sourceId },
        )
        assertEquals(narrowed.sources.sumOf(SourceHealthProfile::maximumRequestCount), narrowed.maxRequests)
        assertEquals(true, selection.allowAuthPending)
        assertEquals(true, selection.useOperationLevelAuthentication)
    }

    @Test
    fun blankSourceArgumentKeepsTheWholePublicProfile() {
        listOf(null, "", "  ").forEach { argument ->
            val selection = ExtensionHealthProfileSelection.selectionFor(
                ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE,
                argument,
            )

            assertEquals(13, selection.select(officialProfile()).sources.size)
        }
    }

    @Test
    fun sourceNarrowingRejectsUnknownMalformedAndNonPublicProfiles() {
        val public = ExtensionHealthProfileSelection.PUBLIC_RECURRING_PROFILE
        val full = officialProfile()

        assertThrows(IllegalArgumentException::class.java) {
            ExtensionHealthProfileSelection.selectionFor(public, "tw.kevinzhang.unknown").select(full)
        }
        listOf(",", " , ", "../secret", "tw.kevinzhang.eyny;rm", "a".repeat(129)).forEach { argument ->
            assertThrows(IllegalArgumentException::class.java) {
                ExtensionHealthProfileSelection.selectionFor(public, argument)
            }
        }
        listOf(null, "official-live-v1", "candidate-eyny-v1", "zero-secret-hackernews-v1").forEach { profile ->
            assertThrows(IllegalArgumentException::class.java) {
                ExtensionHealthProfileSelection.selectionFor(profile, "tw.kevinzhang.eyny")
            }
        }
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
        assertEquals(
            setOf("eyny.com", "www.eyny.com", "www51.eyny.com", "www52.eyny.com", "www53.eyny.com", "www54.eyny.com"),
            public.sources.first { it.sourceId == "tw.kevinzhang.eyny" }.allowedHosts.toSet(),
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
    fun officialProfileCoversReviewedMultiHostBoardCatalogsAndPinsPublicPttBoard() {
        val sources = officialProfile().sources.associateBy(SourceHealthProfile::sourceId)

        assertEquals(
            setOf("eclair.nagatoyuki.org", "selene.zawarudo.org", "www.gomiga.org"),
            sources.getValue("tw.kevinzhang.nagatoyuki").allowedHosts,
        )
        assertEquals(
            setOf("kemono.wtako.net", "rthost.win", "www.karlsland.net"),
            sources.getValue("tw.kevinzhang.wtako").allowedHosts,
        )
        assertEquals("Stock", sources.getValue("tw.kevinzhang.newshub.extension.ptt").boardQuery)
        assertEquals("Stock", sources.getValue("tw.kevinzhang.newshub.extension.ptt").boardNameContains)
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
