package tw.kevinzhang.gamer_api.model

data class GComment(
    val bsn: String,
    val sn: String,
    val userId: String,
    val comment: String,
    val gp: String,
    val bp: String,
    val wtime: String,
    val mtime: String,
    val delreason: String,
    val state: String,
    val floor: Int,
    val type: String,
    val content: String,
    val snB: String,
    val time: String,
    val nick: String,
)