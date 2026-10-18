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
    val refresh: Boolean = false,
    /** Host-issued non-secret generation; never a credential or authorization grant. */
    val contextEpoch: String? = null,
) {
    init {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }
        require(!refresh || pageToken == null) { "Refresh requires the first page" }
    }
}

/** A page of boards in source-defined relevance/popularity order. */
data class BoardPage(
    val boards: List<Board>,
    val nextPageToken: String? = null,
    val catalogInfo: BoardCatalogInfo? = null,
)

enum class BoardCatalogMode { REMOTE_SEARCH, CATALOG, UNKNOWN }
enum class BoardCatalogCoverage { AVAILABLE, PARTIAL, UNKNOWN }

data class BoardCatalogInfo(
    val mode: BoardCatalogMode = BoardCatalogMode.UNKNOWN,
    val updatedAtMillis: Long? = null,
    val isStale: Boolean = false,
    val coverage: BoardCatalogCoverage = BoardCatalogCoverage.UNKNOWN,
    /** Echo after the source has applied this Host-issued catalog generation. */
    val contextEpoch: String? = null,
)
