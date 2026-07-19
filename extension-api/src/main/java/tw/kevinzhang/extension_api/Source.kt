package tw.kevinzhang.extension_api

import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.StateFlow
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
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
     * Legacy login indicator. New extensions should implement [AuthenticatedSource] instead.
     * The host never infers an Activity name from this value.
     */
    val needsLogin: Boolean

    /** Source-defined browse categories. Return an empty list when categories are unsupported. */
    suspend fun getBoardCategories(): List<BoardCategory> = emptyList()

    /**
     * Browses or searches the board catalog one page at a time.
     * An empty [BoardPageRequest.query] text means popular boards, not the entire catalog.
     */
    suspend fun getBoardPage(request: BoardPageRequest): BoardPage
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
     * Legacy runtime hook retained for binary/source compatibility. New sources should
     * implement [SessionAwareSource] and use its source-scoped [SourceRuntime] instead.
     */
    fun onAttach(client: OkHttpClient) {}
}

/** Authentication a source opts into. The host owns the UI and credential storage. */
sealed interface AuthSpec {
    data object None : AuthSpec

    /**
     * A web login whose resulting cookies are used by the source's HTTP client.
     * All hosts must be exact, lower-case host names; wildcards are deliberately unsupported.
     */
    data class WebCookie(
        val loginUrl: String,
        val allowedHosts: Set<String>,
        val cookieOrigins: Set<String>,
        val cookieDomains: Set<String> = emptySet(),
        val javaScriptEnabled: Boolean = true,
    ) : AuthSpec
}

enum class AuthState {
    Unknown,
    SignedOut,
    SigningIn,
    SignedIn,
    Expired,
}

/** Thrown by a source after it has identified an unauthenticated response. */
class AuthenticationRequiredException(
    message: String? = null,
    val isUserAction: Boolean = true,
) : Exception(message)

/** Runtime supplied by the host to each source. */
interface SourceRuntime {
    val httpClient: OkHttpClient
    val authentication: AuthenticationSession
}

/** Per-source authentication state. Calling [markExpired] never launches UI. */
interface AuthenticationSession {
    val state: StateFlow<AuthState>
    fun markExpired()
}

/** Implemented by sources which want a source-scoped HTTP session. */
interface SessionAwareSource : Source {
    fun onAttach(runtime: SourceRuntime) {}
}

/** Implemented by sources which use the host-managed authentication flow. */
interface AuthenticatedSource : SessionAwareSource {
    val authSpec: AuthSpec

    /** Verify the current session using a real protected endpoint, not cookie presence. */
    suspend fun validateSession(): Boolean
}
