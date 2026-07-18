package tw.kevinzhang.extension_api

/**
 * Stable identities for the renamed twocat source.
 *
 * [LEGACY] was shipped by the external extension. [HISTORICAL_LEGACY] was
 * used by the former built-in source, so hosts keep accepting both while
 * users move to [CURRENT].
 */
object TwocatSourceIds {
    const val CURRENT = "tw.kevinzhang.twocat"
    const val LEGACY = "tw.kevinzhang.site2cat"
    const val HISTORICAL_LEGACY = "tw.kevinzhang.2cat"

    val legacyIds: Set<String> = linkedSetOf(LEGACY, HISTORICAL_LEGACY)
    val allIds: Set<String> = linkedSetOf(CURRENT, LEGACY, HISTORICAL_LEGACY)

    fun isTwocatId(id: String): Boolean = id in allIds

    fun canonicalize(id: String): String = if (id in legacyIds) CURRENT else id
}
