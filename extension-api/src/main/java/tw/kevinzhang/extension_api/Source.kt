package tw.kevinzhang.extension_api

import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

interface Source {
    val id: String
    val name: String
    val language: String
    val version: Int
    val iconUrl: String?

    /**
     * If true, the app calls [getComments] with page numbers and expects paginated results.
     * The extension should return posts with empty [Post.comments] from [getThread].
     *
     * If false, the extension returns all comments inside [Post.comments] from [getThread],
     * and the app handles local pagination.
     */
    val supportsCommentPagination: Boolean get() = false

    /**
     * If true, the app always displays the full-resolution [tw.kevinzhang.extension_api.model.Paragraph.ImageInfo.raw]
     * image instead of the thumbnail. Useful for sources where thumbnails are unavailable or
     * where the raw URL is already optimised for display.
     */
    val alwaysUseRawImage: Boolean get() = false

    suspend fun getBoards(): List<Board>
    suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary>
    suspend fun getThread(summary: ThreadSummary): Thread

    /**
     * Called only when [supportsCommentPagination] is true.
     * [page] is 1-based.
     */
    suspend fun getComments(post: Post, page: Int): CommentPage = CommentPage(emptyList(), false)

    /** Returns the publicly accessible web URL for this thread, or null if login is required. */
    fun getWebUrl(summary: ThreadSummary): String? = null

    /** Whether this source requires the user to be logged in. Default: false. */
    val requiresLogin: Boolean get() = false

    /** The login page URL to open in a WebView when [requiresLogin] is true. */
    val loginUrl: String? get() = null

    /**
     * Optional JavaScript to execute after the login page finishes loading.
     * Useful when login is implemented as a JS-triggered modal rather than a separate page
     * (e.g. `"User.Login.requireLoginIframe();"` for Gamer 巴哈姆特).
     */
    val loginPageLoadJs: String? get() = null

    /**
     * Called by the host app after the source is loaded, injecting a [SourceContext] that
     * the source can use to request auth UI. Default implementation is a no-op, so existing
     * extensions are unaffected.
     */
    fun onAttach(context: SourceContext) {}
}
