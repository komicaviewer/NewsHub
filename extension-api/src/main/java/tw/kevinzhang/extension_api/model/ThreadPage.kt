package tw.kevinzhang.extension_api.model

data class ThreadPage(
    val posts: List<Post>,
    val nextPageToken: String?,
    val metadata: ThreadPageMetadata? = null,
)

/** Canonical thread fields normally supplied by the first page or the legacy bridge. */
data class ThreadPageMetadata(
    val id: String,
    val url: String?,
    val title: String?,
)
