package tw.kevinzhang.hub_server.data.post.komica

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.post.Post
import tw.kevinzhang.hub_server.data.toParagraph
import tw.kevinzhang.komica_api.model.KPost

@Entity(tableName = "komica_post")
data class KomicaPost (
    @PrimaryKey val url: String,
    override val threadUrl: String,
    override val id: String,
    override val title: String,
    override val content: List<Paragraph>,
    override val commentsUrl: String,

    /**
     * Returns the comments count count.
     */
    val comments: Int,
    val createdAt: Long?,
    val poster: String?,
    val visits: Int?,

    /**
     * Returns the re-posts count.
     */
    val replies: Int?,
    val readAt: Int?,
    val page: Int,
): Post

fun KPost.toKomicaPost(page: Int, boardUrl: String, threadUrl: String) =
    KomicaPost(
        threadUrl = threadUrl,
        url = url,
        title = title,
        createdAt = createdAt,
        poster = poster,
        visits = visits,
        replies = replies,
        comments = 0,
        readAt = readAt,
        content = content.map { it.toParagraph() },
        commentsUrl = "",
        page = page,
        id = id,
    )

fun mockKomicaPost() =
    KomicaPost(
        threadUrl = "https://gaia.komica.org/00/pixmicat.php?res=29683783",
        url = "https://gaia.komica.org/00/pixmicat.php?res=29683783#r1",
        title = "How to Google?",
        createdAt = 0,
        poster = "Zhen Long",
        visits = 0,
        replies = 10,
        readAt = 0,
        content = listOf(
            Paragraph.Text("Donec id elit non mi porta gravida at eget metus. Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum nibh, ut fermentum massa justo sit amet risus. Etiam porta sem malesuada magna mollis euismod. Donec sed odio dui."),
            Paragraph.ImageInfo(
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png",
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png"
            ),
            Paragraph.Text("This is a template for a simple marketing or informational website. It includes a large callout called the hero unit and three supporting pieces of content. Use it as a starting point to create something more unique."),
        ),
        comments = 0,
        commentsUrl = "",
        page = 1,
        id = "1",
    )