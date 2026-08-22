package tw.kevinzhang.extension_api.model

data class CommentPage(
    val comments: List<Comment>,
    val hasMore: Boolean,
    /** Optional opaque cursor for sources that page a linear comment listing. */
    val nextPageToken: String? = null,
    /** Source-issued expansion points for omitted branches in a nested comment tree. */
    val continuations: List<CommentContinuation> = emptyList(),
)

data class CommentContinuation(
    /** Opaque source-defined token. The Host must return it unchanged. */
    val token: String,
    /** Null for more root comments; otherwise the branch to which loaded comments belong. */
    val parentId: String? = null,
    val remainingCount: Int? = null,
) {
    init {
        require(token.isNotBlank() && token.length <= MAX_COMMENT_CONTINUATION_LENGTH) {
            "Comment continuation token is invalid"
        }
        require(parentId == null || parentId.isNotBlank()) { "Comment continuation parent id is invalid" }
        require(remainingCount == null || remainingCount >= 0) {
            "Comment continuation count is invalid"
        }
    }
}

private const val MAX_COMMENT_CONTINUATION_LENGTH = 16 * 1024
