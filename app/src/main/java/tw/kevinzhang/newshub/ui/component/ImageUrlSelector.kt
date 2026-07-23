package tw.kevinzhang.newshub.ui.component

/**
 * Chooses the URL used to render an inline image.
 *
 * Sources can omit a thumbnail, so the raw URL is always the safe fallback.
 */
internal fun selectImageUrl(
    raw: String,
    thumb: String?,
    alwaysUseRawImage: Boolean,
): String = if (alwaysUseRawImage) raw else thumb ?: raw
