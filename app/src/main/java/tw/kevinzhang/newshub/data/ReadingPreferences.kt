package tw.kevinzhang.newshub.data

/** Controls how thread cards are presented in collection timelines. */
enum class TimelineDisplayMode {
    COMPACT,
    MEDIA_FIRST;

    companion object {
        fun fromStoredValue(value: String?): TimelineDisplayMode =
            entries.firstOrNull { it.name == value } ?: COMPACT
    }
}

/** Controls whether replies are shown chronologically or grouped by their reply relationship. */
enum class ReplyDisplayMode {
    CONTEXTUAL,
    NESTED;

    companion object {
        fun fromStoredValue(value: String?): ReplyDisplayMode =
            entries.firstOrNull { it.name == value } ?: CONTEXTUAL
    }
}

/** Controls when a thread or post is added to the reading history. */
enum class ReadTrackingMode {
    POST_VISIBLE,
    THREAD_OPENED;

    companion object {
        fun fromStoredValue(value: String?): ReadTrackingMode =
            entries.firstOrNull { it.name == value } ?: POST_VISIBLE
    }
}

data class ReadingPreferences(
    val timelineDisplayMode: TimelineDisplayMode = TimelineDisplayMode.COMPACT,
    val replyDisplayMode: ReplyDisplayMode = ReplyDisplayMode.CONTEXTUAL,
    val readTrackingMode: ReadTrackingMode = ReadTrackingMode.POST_VISIBLE,
)
