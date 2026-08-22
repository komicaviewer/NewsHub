package tw.kevinzhang.extension_api.model

/** A provider-neutral feed filter dimension. IDs are source-defined and opaque to the Host. */
data class ThreadFeedFilter(
    val id: String,
    val name: String,
    val options: List<ThreadFeedFilterOption>,
    val defaultOptionId: String,
) {
    init {
        require(id.isFeedId()) { "Feed filter id is invalid" }
        require(name.isNotBlank() && name.length <= MAX_FEED_LABEL_LENGTH) {
            "Feed filter name is invalid"
        }
        require(options.isNotEmpty() && options.size <= MAX_FEED_OPTIONS) {
            "Feed filter options must be non-empty and bounded"
        }
        require(options.map(ThreadFeedFilterOption::id).distinct().size == options.size) {
            "Feed filter option ids must be unique"
        }
        require(options.any { it.id == defaultOptionId }) {
            "Feed filter default must name a declared option"
        }
    }
}

/** One selectable value inside a [ThreadFeedFilter]. */
data class ThreadFeedFilterOption(
    val id: String,
    val name: String,
) {
    init {
        require(id.isFeedId()) { "Feed filter option id is invalid" }
        require(name.isNotBlank() && name.length <= MAX_FEED_LABEL_LENGTH) {
            "Feed filter option name is invalid"
        }
    }
}

/**
 * Requests a feed page. Filter keys and values must come from [ThreadFeedFilter] declarations;
 * [pageToken] is returned verbatim by [ThreadSummaryPage.nextPageToken].
 */
data class ThreadSummaryPageRequest(
    val board: Board,
    val filters: Map<String, String> = emptyMap(),
    val pageToken: String? = null,
    val pageSize: Int = 30,
) {
    init {
        require(pageSize in 1..MAX_FEED_PAGE_SIZE) { "pageSize must be bounded and positive" }
        require(filters.size <= MAX_FEED_FILTERS) { "Too many feed filters" }
        require(filters.all { (key, value) -> key.isFeedId() && value.isFeedId() }) {
            "Feed filter selection is invalid"
        }
        require(pageToken == null || pageToken.isNotBlank() && pageToken.length <= MAX_PAGE_TOKEN_LENGTH) {
            "Feed page token is invalid"
        }
    }
}

/** One opaque-cursor page of thread summaries. */
data class ThreadSummaryPage(
    val summaries: List<ThreadSummary>,
    val nextPageToken: String? = null,
)

private fun String.isFeedId(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))

private const val MAX_FEED_FILTERS = 16
private const val MAX_FEED_OPTIONS = 64
private const val MAX_FEED_LABEL_LENGTH = 128
private const val MAX_FEED_PAGE_SIZE = 100
private const val MAX_PAGE_TOKEN_LENGTH = 4096
