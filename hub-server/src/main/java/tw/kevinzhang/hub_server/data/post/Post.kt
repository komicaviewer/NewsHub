package tw.kevinzhang.hub_server.data.post

import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.trySubstring

interface Post {

    /**
     * Which thread url the post belongs to.
     */
    val threadUrl: String

    /**
     * For request comment list.
     */
    val commentsUrl: String

    /**
     * Returns the id in the discussion thread.
     */
    val id: String

    /**
     * Returns the content and it's contain the reply target [Paragraph.ReplyTo].
     */
    val content: List<Paragraph>

    val title: String
}

fun List<Post>.parentIs(parentId: String): List<Post> {
    return this.filter { it.parent().contains(parentId) }
}

fun Post.parent(): List<String> {
    return content
        .filterIsInstance<Paragraph.ReplyTo>()
        .map { paragraph -> paragraph.id }
}

fun Post.toText(): String {
    return content
        .filterIsInstance<Paragraph.Text>()
        .firstOrNull()
        ?.content
        ?.trySubstring(0..5)
        ?: ""
}