package tw.kevinzhang.hub_server.data.post.gamer

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.kevinzhang.gamer_api.model.GPost
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.post.Post
import tw.kevinzhang.hub_server.data.toParagraph

@Entity(tableName = "gamer_post")
data class GamerPost (
    @PrimaryKey val url: String,
    override val threadUrl: String,
    override val id: String,
    override val title: String,
    override val commentsUrl: String,
    val createdAt: Long?,
    val posterName: String,
    val replies: Int?,
    val comments: Int?,
    val readAt: Int?,
    override val content: List<Paragraph>,
    val page: Int,
): Post

fun GPost.toGamerPost(threadUrl: String) =
    GamerPost(
        threadUrl = threadUrl,
        url = url,
        title = title,
        createdAt = createdAt,
        replies = replies,
        readAt = readAt,
        content = content.map { it.toParagraph() },
        page = page,
        id = id,
        posterName = posterName,
        commentsUrl = commentsUrl,
        comments = comments,
    )

fun mockGamerPost() =
    GamerPost(
        id = "29683783",
        posterName = "Zhen Long",
        threadUrl = "https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175",
        title = "How to Google?",
        url = "https://forum.gamer.com.tw/C.php?bsn=60076&snA=4166175#29683783",
        createdAt = 0,
        replies = 0,
        readAt = 0,
        content = listOf(
            Paragraph.Text("Donec id elit non mi porta gravida at eget metus. Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum nibh, ut fermentum massa justo sit amet risus. Etiam porta sem malesuada magna mollis euismod. Donec sed odio dui."),
            Paragraph.ImageInfo(
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png",
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png"
            ),
            Paragraph.Text("This is a template for a simple marketing or informational website. It includes a large callout called the hero unit and three supporting pieces of content. Use it as a starting point to create something more unique."),
        ),
        page = 1,
        commentsUrl = "https://forum.gamer.com.tw/ajax/moreCommend.php?bsn=60076&snB=89090465",
        comments = 0,
    )