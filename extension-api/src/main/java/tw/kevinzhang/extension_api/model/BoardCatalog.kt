package tw.kevinzhang.extension_api.model

/** A source-defined board category. IDs only need to be stable within that source. */
data class BoardCategory(
    val id: String,
    val name: String,
)

/**
 * A board catalog query interpreted by one [tw.kevinzhang.extension_api.Source].
 * An empty [text] requests that source's popular boards. [categoryId] is source-defined.
 */
data class BoardQuery(
    val text: String = "",
    val categoryId: String? = null,
)

/**
 * A single page request. [pageToken] is null for the first page and otherwise comes verbatim
 * from [BoardPage.nextPageToken].
 */
data class BoardPageRequest(
    val query: BoardQuery = BoardQuery(),
    val pageToken: String? = null,
    val pageSize: Int = 30,
) {
    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }
}

/** A page of boards in source-defined relevance/popularity order. */
data class BoardPage(
    val boards: List<Board>,
    val nextPageToken: String? = null,
)
