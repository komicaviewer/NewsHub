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
        val useOperationLevelAuthentication: Boolean = false,
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
            val effectiveSources = if (useOperationLevelAuthentication) {
                selectedSources.map { source ->
                    if (source.authenticatedOperations.isEmpty()) source
                    else source.copy(requireAuthenticatedSession = false)
                }
            } else {
                selectedSources
            }
            return profile.copy(
                profileId = profileId,
                maxRequests = effectiveSources.sumOf(SourceHealthProfile::maximumRequestCount),
                sources = effectiveSources,
            ).validated()
        }
    }

    private const val FULL_ASSET = "extension-health/profile-v1.json"
    private val selectionByProfile = listOf(
        Selection(FULL_PROFILE, FULL_ASSET),
        Selection(
            PUBLIC_RECURRING_PROFILE,
            FULL_ASSET,
            allowAuthPending = true,
            useOperationLevelAuthentication = true,
        ),
        Selection(ZERO_SECRET_HACKERNEWS_PROFILE, "extension-health/profile-hackernews-v1.json"),
        candidate(
            "candidate-eyny-v1",
            "tw.kevinzhang.newshub.extension.eyny",
            "tw.kevinzhang.eyny",
            allowAuthPending = true,
            useOperationLevelAuthentication = true,
        ),
        candidate(
            "candidate-gamer-v1",
            "tw.kevinzhang.newshub.extension.gamer",
            "tw.kevinzhang.newshub.extension.gamer",
            allowAuthPending = true,
            useOperationLevelAuthentication = true,
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
            "candidate-komica-twocat-v1",
            "tw.kevinzhang.newshub.extension.komica",
            "tw.kevinzhang.komica.twocat",
        ),
        candidate(
            "candidate-komica2-v1",
            "tw.kevinzhang.newshub.extension.komica2",
            "tw.kevinzhang.komica2.twocat",
            "tw.kevinzhang.komica2.sora",
            "tw.kevinzhang.komica2.zawarudo",
        ),
        candidate(
            "candidate-komica2-twocat-v1",
            "tw.kevinzhang.newshub.extension.komica2",
            "tw.kevinzhang.komica2.twocat",
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
        useOperationLevelAuthentication: Boolean = false,
    ) = Selection(
        profileId = profileId,
        assetPath = FULL_ASSET,
        packageName = packageName,
        sourceIds = sourceIds.toCollection(linkedSetOf()),
        allowAuthPending = allowAuthPending,
        useOperationLevelAuthentication = useOperationLevelAuthentication,
    )

    fun selectionFor(argument: String?): Selection = selectionByProfile[
        argument ?: FULL_PROFILE
    ] ?: throw IllegalArgumentException("Unknown extension health profile")

    private val SOURCE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")
    private const val MAX_NARROWED_SOURCES = 32

    /**
     * Narrows only the public recurring profile to a caller-chosen subset of its own Sources.
     * The subset is validated against the profile at select time, so it can never add a Source,
     * host, package or policy that the closed profile does not already contain.
     */
    fun selectionFor(argument: String?, sourceIdsArgument: String?): Selection {
        val base = selectionFor(argument)
        if (sourceIdsArgument.isNullOrBlank()) return base
        require(base.profileId == PUBLIC_RECURRING_PROFILE) {
            "Only the public recurring health profile can be narrowed"
        }
        val requested = sourceIdsArgument.split(',').map(String::trim)
        require(requested.all(SOURCE_ID_PATTERN::matches)) { "Malformed health Source id" }
        val unique = requested.toCollection(linkedSetOf())
        require(unique.size <= MAX_NARROWED_SOURCES) { "Too many health Source ids" }
        return base.copy(sourceIds = unique)
    }

    fun assetFor(argument: String?): String = selectionFor(argument).assetPath
}
