package tw.kevinzhang.extension_api

import okhttp3.OkHttpClient
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
    val supportsCommentPagination: Boolean

    /**
     * If true, the app always displays the full-resolution [tw.kevinzhang.extension_api.model.Paragraph.ImageInfo.raw]
     * image instead of the thumbnail. Useful for sources where thumbnails are unavailable or
     * where the raw URL is already optimised for display.
     */
    val alwaysUseRawImage: Boolean

    /**
     * If true, the host app will launch this extension's LoginActivity (by package name
     * convention: `{source.id}.LoginActivity`) when the user needs to authenticate.
     * For extension sources, [id] must match the APK package name.
     */
    val needsLogin: Boolean

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

    /**
     * Called by the host app after the source is loaded, injecting a shared [OkHttpClient]
     * that includes the host app's persistent cookie jar. Sources that require login should
     * use this client for all HTTP requests so that login cookies are sent automatically.
     * Default implementation is a no-op, so built-in sources are unaffected.
     */
    fun onAttach(client: OkHttpClient) {}
}
