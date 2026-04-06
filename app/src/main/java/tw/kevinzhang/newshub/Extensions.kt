package tw.kevinzhang.newshub

import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import java.net.URLEncoder

fun String.encode(): String? = URLEncoder.encode(this, "utf-8")

fun List<Post>.filterRepliesBy(threadId: String?): List<Post> {
    return if (threadId == null)
        this.filter { it.replyTo().isEmpty() }
    else
        this.filter { it.replyTo().contains(threadId) }
}

fun Post.replyTo(): List<String> {
    return content
        .filterIsInstance<Paragraph.ReplyTo>()
        .map { paragraph -> paragraph.targetId }
}
