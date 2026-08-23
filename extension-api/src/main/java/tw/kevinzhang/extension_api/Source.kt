package tw.kevinzhang.extension_api

import kotlinx.coroutines.flow.StateFlow
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.CommentContinuation
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadPage
import tw.kevinzhang.extension_api.model.ThreadPageMetadata
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.extension_api.model.ThreadFeedFilter
import tw.kevinzhang.extension_api.model.ThreadSummaryPage
import tw.kevinzhang.extension_api.model.ThreadSummaryPageRequest

interface Source {
    /** Host-verified runtime identity. In-process extension implementations leave this null. */
    val sourceIdentity: SourceIdentity?
        get() = null
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
     * True when normal content use requires an authenticated session. Authentication capability
     * is declared independently by implementing [AuthenticatedSource]; optional-login Sources
     * implement that interface and leave this false. Sources must still identify unauthenticated
     * responses and throw [AuthenticationRequiredException] rather than treating this UI hint as
     * an authorization boundary.
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

    /**
     * Describes source-owned feed filter dimensions such as sort order or time range. IDs are
     * opaque to the Host and are sent back unchanged in [ThreadSummaryPageRequest.filters].
     */
    suspend fun getThreadFeedFilters(board: Board): List<ThreadFeedFilter> = emptyList()

    /**
     * Loads one cursor-based feed page. [ThreadSummaryPageRequest.pageToken] is source-defined and
     * opaque to the Host.
     *
     * This default implementation bridges legacy integer-page Sources. Legacy Sources only accept
     * their declared default filters; a non-empty filter selection is therefore unsupported.
     */
    suspend fun getThreadSummaryPage(request: ThreadSummaryPageRequest): ThreadSummaryPage {
        if (request.filters.isNotEmpty()) {
            throw UnsupportedOperationException("This source does not support feed filters")
        }
        val page = request.pageToken?.let(::decodeLegacyThreadSummaryPageToken) ?: 1
        val summaries = getThreadSummaries(request.board, page)
        return ThreadSummaryPage(
            summaries = summaries,
            nextPageToken = if (summaries.isEmpty()) null else encodeLegacyThreadSummaryPageToken(page + 1),
        )
    }
    suspend fun getThread(summary: ThreadSummary): Thread

    /**
     * Loads a page of posts for [summary]. [pageToken] is source-defined and opaque to the host.
     *
     * The default null-token bridge preserves the legacy [getThread] contract. Sources that
     * support additional pages must override this method and handle their own page tokens.
     */
    suspend fun getThreadPage(summary: ThreadSummary, pageToken: String?): ThreadPage {
        if (pageToken != null) {
            throw UnsupportedOperationException("This source does not support thread pagination")
        }
        val thread = getThread(summary)
        return ThreadPage(
            posts = thread.posts,
            nextPageToken = null,
            metadata = ThreadPageMetadata(
                id = thread.id,
                url = thread.url,
                title = thread.title,
            ),
        )
    }

    /**
     * Called only when [supportsCommentPagination] is true.
     * [page] is 1-based.
     */
    suspend fun getComments(post: Post, page: Int): CommentPage = CommentPage(emptyList(), false)

    /** Loads comments represented by a source-issued opaque continuation. */
    suspend fun getCommentContinuation(post: Post, continuation: CommentContinuation): CommentPage =
        throw UnsupportedOperationException("This source does not support comment continuations")

    /** Returns the public website URL for [board]. The Host turns it into a scoped opaque handle. */
    suspend fun getBoardWebUrl(board: Board): String? = board.url

    /** Returns the publicly accessible web URL for this thread, or null if login is required. */
    suspend fun getWebUrl(summary: ThreadSummary): String? = null

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

    /**
     * A provider-neutral OAuth registration owned by the Host. The provider endpoints, redirect
     * URI, PKCE verifier, client credentials, tokens, refresh policy, and credential injection are
     * never supplied by or exposed to the isolated extension.
     */
    data class OAuth(
        val providerId: String,
        val clientRegistrationId: String,
        val scopes: Set<String>,
    ) : AuthSpec

    /**
     * A provider-neutral OAuth 1.0a registration owned by the Host. Consumer credentials,
     * temporary request-token secrets, access-token secrets, signatures, and callback
     * verification never cross the isolated extension boundary.
     */
    data class OAuth1(
        val providerId: String,
        val clientRegistrationId: String,
    ) : AuthSpec
}

/**
 * Optional source capability for sites which bind authenticated cookies to the browser
 * User-Agent. The host applies this value before the login WebView's first navigation; the
 * source must use the exact same value for authenticated HTTP requests.
 */
interface WebLoginUserAgentProvider {
    val webLoginUserAgent: String
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
    /** The only ambient capability available inside an isolated extension process. */
    val network: SourceNetwork
    /** Identity-bound, Host-owned operations. This never exposes a generic cookie jar. */
    val namedCookies: NamedCookieCapability
        get() = UnsupportedNamedCookieCapability
    val authentication: AuthenticationSession
}

data class EynyChallengeProof(
    val host: String,
    val cookiePrefix: String,
    val nonce: Long,
    val timestamp: String,
    val challenge: String,
) {
    init {
        require(host in EYNY_CHALLENGE_HOSTS) { "Unsupported EYNY host" }
        require(cookiePrefix.matches(Regex("[a-f0-9]{6,16}"))) { "Invalid EYNY cookie prefix" }
        require(nonce in 0..2_000_000L) { "Invalid EYNY nonce" }
        require(timestamp.matches(Regex("[0-9]{6,20}"))) { "Invalid EYNY timestamp" }
        require(challenge.matches(Regex("[a-fA-F0-9]{16,128}"))) { "Invalid EYNY challenge" }
    }
}

private val EYNY_CHALLENGE_HOSTS = setOf(
    "eyny.com",
    "www.eyny.com",
    "www52.eyny.com",
    "www53.eyny.com",
)

interface NamedCookieCapability {
    /** Reveals only whether the exact PTT consent cookie would be sent to www.ptt.cc. */
    suspend fun hasPttAdultConsent(): Boolean

    /** Blindly stores one bounded EYNY challenge proof under Host-fixed cookie attributes. */
    suspend fun storeEynyChallengeProof(proof: EynyChallengeProof)
}

private object UnsupportedNamedCookieCapability : NamedCookieCapability {
    override suspend fun hasPttAdultConsent(): Boolean =
        throw UnsupportedOperationException("PTT consent capability is unavailable")

    override suspend fun storeEynyChallengeProof(proof: EynyChallengeProof): Nothing =
        throw UnsupportedOperationException("EYNY challenge capability is unavailable")
}

data class SourceNetworkRequest(
    val operation: String,
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

data class SourceNetworkResponse(
    val code: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray,
)

/**
 * Requests are authorized by a Host-owned, immutable policy bound to the current Source
 * service. Implementations must not assume that arbitrary URLs, methods, or headers are allowed.
 */
fun interface SourceNetwork {
    suspend fun execute(request: SourceNetworkRequest): SourceNetworkResponse
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

private const val LEGACY_THREAD_SUMMARY_PAGE_PREFIX = "newshub-legacy-page:"

private fun encodeLegacyThreadSummaryPageToken(page: Int): String =
    "$LEGACY_THREAD_SUMMARY_PAGE_PREFIX$page"

private fun decodeLegacyThreadSummaryPageToken(token: String): Int {
    val page = token.removePrefix(LEGACY_THREAD_SUMMARY_PAGE_PREFIX)
        .takeIf { token.startsWith(LEGACY_THREAD_SUMMARY_PAGE_PREFIX) }
        ?.toIntOrNull()
    require(page != null && page > 1) { "Invalid legacy thread summary page token" }
    return page
}
