package tw.kevinzhang.extension_api.model

data class Board(
    val sourceId: String,
    val url: String,
    val name: String,
    val description: String? = null,
)
