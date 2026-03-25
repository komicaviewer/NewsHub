package tw.kevinzhang.extension_api.model

data class Thread(
    val id: String,
    val title: String?,
    val posts: List<Post>,  // posts[0] is the OP
)
