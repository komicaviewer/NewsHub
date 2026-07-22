package tw.kevinzhang.newshub.ui.component

/** Presentation metadata shared by timeline, history, and saved-post cards. */
internal data class TimelineCardMetadata(
    val sourceName: String?,
    val boardName: String?,
    val alwaysUseRawImage: Boolean,
)

/**
 * Keeps persisted source and board names together with the currently installed source's image
 * policy. Names are snapshots, so existing history remains understandable after an extension is
 * removed or renamed.
 */
internal fun timelineCardMetadata(
    sourceId: String,
    sourceName: String?,
    boardName: String?,
    rawImageSourceIds: Set<String>,
) = TimelineCardMetadata(
    sourceName = sourceName,
    boardName = boardName,
    alwaysUseRawImage = sourceId in rawImageSourceIds,
)
