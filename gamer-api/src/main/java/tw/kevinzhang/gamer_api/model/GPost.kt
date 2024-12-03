package tw.kevinzhang.gamer_api.model

data class GPost (
    val id: String,
    val url: String,
    val title: String,
    val createdAt: Long,
    val posterName: String,
    val posterId: String,
    val like: Int,
    val unlike: Int,
    var replies: Int,
    val comments: Int,
    val commentsUrl: String,
    val readAt: Int,
    val page: Int,
    val content: List<GParagraph>,
)

class GPostBuilder {
    private var id: String= ""
    private var url: String= ""
    private var title: String= ""
    private var createdAt: Long= 0L
    private var posterId: String= ""
    private var posterName: String= ""
    private var like: Int= 0
    private var unlike: Int= 0
    private var replies: Int= 0
    private var readAt: Int= 0
    private var comments: Int= 0
    private var commentsUrl: String= ""
    private var content: List<GParagraph> = emptyList()
    private var page: Int= 0

    fun setTitle(title: String): GPostBuilder {
        this.title = title
        return this
    }

    fun setPosterName(posterName: String): GPostBuilder {
        this.posterName = posterName
        return this
    }

    fun setPosterId(posterId: String): GPostBuilder {
        this.posterId = posterId
        return this
    }

    fun setCreatedAt(createdAt: Long): GPostBuilder {
        this.createdAt = createdAt
        return this
    }

    fun setContent(content: List<GParagraph>): GPostBuilder {
        this.content = content
        return this
    }

    fun setLike(number: Int) {
        this.like = number
    }

    fun setComments(number: Int) {
        this.comments = number
    }

    fun setCommentsUrl(string: String) {
        this.commentsUrl = string
    }

    fun setPage(number: Int) {
        this.page = number
    }

    fun setUnlike(number: Int) {
        this.unlike = number
    }

    fun addContent(content: GParagraph): GPostBuilder {
        this.content = this.content.plus(content)
        return this
    }

    fun setUrl(url: String): GPostBuilder {
        this.url = url
        return this
    }

    fun setPostId(postId: String): GPostBuilder {
        this.id = postId
        return this
    }

    fun build() =
        GPost(
            id,
            url,
            title,
            createdAt,
            posterName,
            posterId,
            like,
            unlike,
            replies,
            comments,
            commentsUrl,
            readAt,
            page,
            content,
        )
}