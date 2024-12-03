package tw.kevinzhang.hub_server.data.comment

import tw.kevinzhang.hub_server.data.Paragraph

interface Comment {
    /**
     * Returns the id in the comment thread.
     */
    val id: String

    /**
     * Returns the content and it's contain the reply target [Paragraph.ReplyTo].
     */
    val content: List<Paragraph>
}