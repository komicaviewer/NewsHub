package tw.kevinzhang.hub_server.data.post

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tw.kevinzhang.hub_server.data.Paragraph

internal class PostExtensionsTest {

    @Test
    fun `Test PostList filterReplyToIs extensions with correct target expect successful`() {
        val post1 = object: Post {
            override val id = "D"
            override val threadUrl = "https://gaia.komica.org/00/pixmicat.php?res=D"
            override val commentsUrl = "https://gaia.komica.org/00/pixmicat.php?res=D?morecomment=true"
            override val title = "title"
            override val content = listOf(
                Paragraph.ReplyTo("A"),
                Paragraph.ReplyTo("B"),
                Paragraph.ReplyTo("C"),
            )
        }

        val post2 = object: Post {
            override val id = "C"
            override val threadUrl = "https://gaia.komica.org/00/pixmicat.php?res=C"
            override val commentsUrl = "https://gaia.komica.org/00/pixmicat.php?res=C?morecomment=true"
            override val title = "title"
            override val content = listOf(
                Paragraph.ReplyTo("A"),
                Paragraph.ReplyTo("B"),
            )
        }

        val post3 = object: Post {
            override val id = "B"
            override val threadUrl = "https://gaia.komica.org/00/pixmicat.php?res=B"
            override val commentsUrl = "https://gaia.komica.org/00/pixmicat.php?res=B?morecomment=true"
            override val title = "title"
            override val content = listOf(
                Paragraph.ReplyTo("A"),
            )
        }

        assertEquals(2, listOf(post1, post2, post3).parentIs("B").size)
    }
}