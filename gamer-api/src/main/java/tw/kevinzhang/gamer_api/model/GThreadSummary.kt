package tw.kevinzhang.gamer_api.model

data class GThreadSummary(
    val title: String,
    val url: String,
    val preview: String,
    val gp: Int,
    val thumb: String?,

    /**
     * 留言 + gp + bp 數
     */
    val interactions: Int,

    val popularity: Int,
    val posterName: String,
    val createdAt: String
)