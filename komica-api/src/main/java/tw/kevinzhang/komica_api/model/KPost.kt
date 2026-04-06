package tw.kevinzhang.komica_api.model

data class KPost (
    val id: String,
    val url: String,
    val title: String,
    val createdAt: Long,
    val poster: String,
    val visits: Int,
    var replies: Int,
    val readAt: Int,
    val content: List<KParagraph>,
)

fun KPost.replyTo(): List<String> {
    return content
        .filterIsInstance<KReplyTo>()
        .map { paragraph -> paragraph.targetId }
}

fun List<KPost>.filterRepliesBy(threadId: String?): List<KPost> {
    return if(threadId == null)
        this.filter { it.replyTo().isEmpty() }
    else
        this.filter { it.replyTo().contains(threadId) }
}

class KPostBuilder {
    private var id: String= ""
    private var url: String= ""
    private var title: String= ""
    private var createdAt: Long= 0L
    private var poster: String= ""
    private var visits: Int= 0
    private var replies: Int= 0
    private var readAt: Int= 0
    private var content: List<KParagraph> = emptyList()

    fun setTitle(title: String): KPostBuilder {
        this.title = title
        return this
    }

    fun setPoster(poster: String): KPostBuilder {
        this.poster = poster
        return this
    }

    fun setCreatedAt(createdAt: Long): KPostBuilder {
        this.createdAt = createdAt
        return this
    }

    fun setContent(content: List<KParagraph>): KPostBuilder {
        this.content = content
        return this
    }

    fun addContent(content: KParagraph): KPostBuilder {
        this.content = this.content.plus(content)
        return this
    }

    fun setUrl(url: String): KPostBuilder {
        this.url = url
        return this
    }

    fun setPostId(postId: String): KPostBuilder {
        this.id = postId
        return this
    }

    fun build() =
        KPost(
            id,
            url,
            title,
            createdAt,
            poster,
            visits,
            replies,
            readAt,
            content,
        )
}