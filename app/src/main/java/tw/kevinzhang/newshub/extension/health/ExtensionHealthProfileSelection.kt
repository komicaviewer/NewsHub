package tw.kevinzhang.newshub.extension.health

/** Closed catalog of Host-owned profiles selectable by a non-sensitive instrumentation arg. */
object ExtensionHealthProfileSelection {
    const val FULL_PROFILE = "official-live-v1"
    const val PUBLIC_RECURRING_PROFILE = "official-public-v1"
    const val ZERO_SECRET_HACKERNEWS_PROFILE = "zero-secret-hackernews-v1"

    data class Selection(
        val profileId: String,
        val assetPath: String,
        val packageName: String? = null,
        val sourceIds: Set<String>? = null,
        val allowAuthPending: Boolean = false,
    ) {
        fun select(profile: ExtensionHealthProfile): ExtensionHealthProfile {
            val selectedSources = sourceIds?.let { expected ->
                profile.sources.filter { it.sourceId in expected }.also { selected ->
                    require(selected.mapTo(linkedSetOf()) { it.sourceId } == expected) {
                        "Closed health profile Source set mismatch"
                    }
                    require(packageName == null || selected.all { it.packageName == packageName }) {
                        "Closed health profile package mismatch"
                    }
                }
            } ?: profile.sources
            return profile.copy(
                profileId = profileId,
                maxRequests = selectedSources.fold(0) { total, source ->
                    total + if (source.requireAuthenticatedSession) 4 else 3
                },
                sources = selectedSources,
            ).validated()
        }
    }

    private const val FULL_ASSET = "extension-health/profile-v1.json"
    private val selectionByProfile = listOf(
        Selection(FULL_PROFILE, FULL_ASSET),
        Selection(PUBLIC_RECURRING_PROFILE, FULL_ASSET, allowAuthPending = true),
        Selection(ZERO_SECRET_HACKERNEWS_PROFILE, "extension-health/profile-hackernews-v1.json"),
        candidate(
            "candidate-eyny-v1",
            "tw.kevinzhang.newshub.extension.eyny",
            "tw.kevinzhang.eyny",
            allowAuthPending = true,
        ),
        candidate(
            "candidate-gamer-v1",
            "tw.kevinzhang.newshub.extension.gamer",
            "tw.kevinzhang.newshub.extension.gamer",
            allowAuthPending = true,
        ),
        candidate(
            "candidate-hackernews-v1",
            "tw.kevinzhang.newshub.extension.hackernews",
            "tw.kevinzhang.newshub.extension.hackernews",
        ),
        candidate(
            "candidate-komica-v1",
            "tw.kevinzhang.newshub.extension.komica",
            "tw.kevinzhang.komica.twocat",
            "tw.kevinzhang.komica.sora",
            "tw.kevinzhang.akraft",
            "tw.kevinzhang.nagatoyuki",
            "tw.kevinzhang.wtako",
        ),
        candidate(
            "candidate-komica2-v1",
            "tw.kevinzhang.newshub.extension.komica2",
            "tw.kevinzhang.komica2.twocat",
            "tw.kevinzhang.komica2.sora",
            "tw.kevinzhang.komica2.zawarudo",
        ),
        candidate(
            "candidate-mobile01-v1",
            "tw.kevinzhang.newshub.extension.mobile01",
            "tw.kevinzhang.mobile01",
        ),
        candidate(
            "candidate-ptt-v1",
            "tw.kevinzhang.newshub.extension.ptt",
            "tw.kevinzhang.newshub.extension.ptt",
        ),
    ).associateBy(Selection::profileId)

    private fun candidate(
        profileId: String,
        packageName: String,
        vararg sourceIds: String,
        allowAuthPending: Boolean = false,
    ) = Selection(
        profileId = profileId,
        assetPath = FULL_ASSET,
        packageName = packageName,
        sourceIds = sourceIds.toCollection(linkedSetOf()),
        allowAuthPending = allowAuthPending,
    )

    fun selectionFor(argument: String?): Selection = selectionByProfile[
        argument ?: FULL_PROFILE
    ] ?: throw IllegalArgumentException("Unknown extension health profile")

    fun assetFor(argument: String?): String = selectionFor(argument).assetPath
}
