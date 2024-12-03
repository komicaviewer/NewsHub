package tw.kevinzhang.hub_server.data.comment.gamer

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.kevinzhang.gamer_api.model.GComment
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.comment.Comment

@Entity(tableName = "gamer_comment")
data class GamerComment(
    @PrimaryKey override val id: String,
    val createdAt: Long?,
    val posterName: String,
    val replies: Int?,
    override val content: List<Paragraph>,
    val page: Int,
): Comment

fun GComment.toGamerComment(page: Int) =
    GamerComment(
        id = sn,
        createdAt = 0L,
        posterName = nick,
        replies = 0,
        content = listOf(Paragraph.Text(comment)),
        page = page,
    )

fun mockGamerComment() =
    GamerComment(
        id = "29683783",
        createdAt = 0,
        posterName = "Zhen Long",
        replies = 0,
        content = listOf(
            Paragraph.Text("Donec id elit non mi porta gravida at eget metus. Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum nibh, ut fermentum massa justo sit amet risus. Etiam porta sem malesuada magna mollis euismod. Donec sed odio dui."),
            Paragraph.ImageInfo(
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png",
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png"
            ),
            Paragraph.Text("This is a template for a simple marketing or informational website. It includes a large callout called the hero unit and three supporting pieces of content. Use it as a starting point to create something more unique."),
        ),
        page = 1,
    )