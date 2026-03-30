package tw.kevinzhang.extension_api.model

data class CommentPage(
    val comments: List<Comment>,
    val hasMore: Boolean,
)
