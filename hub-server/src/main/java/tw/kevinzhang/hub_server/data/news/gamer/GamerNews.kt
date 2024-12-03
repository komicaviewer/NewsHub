package tw.kevinzhang.hub_server.data.news.gamer

import androidx.room.Entity
import androidx.room.PrimaryKey
import tw.kevinzhang.gamer_api.model.GNews
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.news.News

@Entity(tableName = "gamer_news")
data class GamerNews (
    @PrimaryKey override val threadUrl: String,
    override val boardUrl: String,
    override val content: List<Paragraph>,
    val title: String,
    val createdAt: String,
    val posterName: String?,
    val gp: Int,
    val interactions: Int,
    val popularity: Int,
    val page: Int,
): News

fun GNews.toGamerPost(page: Int, boardUrl: String): GamerNews {
    val content2 = mutableListOf<Paragraph>()
    if (preview != null) {
        content2.add(Paragraph.Text(preview))
    }
    if (thumb != null) {
        content2.add(Paragraph.ImageInfo(thumb = thumb!!, raw = ""))
    }
    return GamerNews(
        boardUrl = boardUrl,
        threadUrl = url,
        title = title,
        createdAt = createdAt,
        posterName = posterName,
        content = content2,
        gp = gp,
        interactions = interactions,
        popularity = popularity,
        page = page,
    )
}

fun mockGamerNews() =
    GamerNews(
        threadUrl = "https://gaia.komica.org/00/pixmicat.php?res=29683783",
        title = "How to Google?",
        boardUrl = "https://gaia.komica.org/00",
        createdAt = "今日 15:19",
        posterName = "Zhen Long",
        content = listOf(
            Paragraph.Text("Donec id elit non mi porta gravida at eget metus. Fusce dapibus, tellus ac cursus commodo, tortor mauris condimentum nibh, ut fermentum massa justo sit amet risus. Etiam porta sem malesuada magna mollis euismod. Donec sed odio dui."),
            Paragraph.ImageInfo(
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png",
                "https://pic-go-bed.oss-cn-beijing.aliyuncs.com/img/20220316151929.png"
            ),
            Paragraph.Text("This is a template for a simple marketing or informational website. It includes a large callout called the hero unit and three supporting pieces of content. Use it as a starting point to create something more unique."),
        ),
        page = 1,
        gp = 0,
        interactions = 0,
        popularity = 0,
    )