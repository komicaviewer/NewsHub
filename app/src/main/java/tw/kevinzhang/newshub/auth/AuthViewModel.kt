package tw.kevinzhang.newshub.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.WebLoginUserAgentProvider
import tw.kevinzhang.extension_loader.ExtensionLoader
import tw.kevinzhang.newshub.auth.oauth.OAuthAuthorizationLaunch
import tw.kevinzhang.newshub.auth.oauth.OAuthCoordinator
import javax.inject.Inject

data class WebLoginRequest(
    val sourceId: String,
    val spec: AuthSpec.WebCookie,
    val userAgent: String? = null,
)

/** Reject malformed extension-provided header values while preserving an accepted value exactly. */
internal fun Any.webLoginUserAgentOrNull(): String? =
    (this as? WebLoginUserAgentProvider)?.webLoginUserAgent?.takeIf { value ->
        value.isNotBlank() && value.length <= 512 && '\r' !in value && '\n' !in value
    }

enum class WebLoginPhase {
    Idle,
    Browsing,
    Verifying,
}

/**
 * Stateful UI contract for the in-host WebView login.
 *
 * Keeping [request] while [phase] is [WebLoginPhase.Verifying] or after an error lets the UI
 * retain the current WebView page and its entered form values. A new collector therefore gets
 * the pending login request instead of missing it as it could with a one-shot event flow.
 */
data class WebLoginUiState(
    val request: WebLoginRequest? = null,
    val phase: WebLoginPhase = WebLoginPhase.Idle,
    val errorMessage: String? = null,
) {
    val isVerifying: Boolean get() = phase == WebLoginPhase.Verifying
}

/** Pure transitions kept separate so the login UI contract remains straightforward to test. */
internal object WebLoginStateReducer {
    fun begin(request: WebLoginRequest) = WebLoginUiState(
        request = request,
        phase = WebLoginPhase.Browsing,
    )

    fun beginVerification(current: WebLoginUiState): WebLoginUiState = current.request
        ?.let { current.copy(phase = WebLoginPhase.Verifying, errorMessage = null) }
        ?: current

    fun fail(current: WebLoginUiState, errorMessage: String): WebLoginUiState = current.request
        ?.let { current.copy(phase = WebLoginPhase.Browsing, errorMessage = errorMessage) }
        ?: current

    fun clear() = WebLoginUiState()
}

data class OAuthLoginUiState(
    val launch: OAuthAuthorizationLaunch? = null,
    val errorMessage: String? = null,
)

/** Coordinates authentication requests; it deliberately contains no Android WebView code. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val sessionManager: SourceSessionManager,
    private val oauthCoordinator: OAuthCoordinator,
) : ViewModel() {
    val authStates = sessionManager.states
    private val _webLoginUiState = MutableStateFlow(WebLoginStateReducer.clear())
    val webLoginUiState: StateFlow<WebLoginUiState> = _webLoginUiState.asStateFlow()
    private val _oauthLoginUiState = MutableStateFlow(OAuthLoginUiState())
    val oauthLoginUiState: StateFlow<OAuthLoginUiState> = _oauthLoginUiState.asStateFlow()

    private var validationJob: Job? = null
    /** Invalidates any late result from a cancelled/replaced login validation. */
    private var loginGeneration = 0L

    /** Called exclusively from a user action in Boards. */
    fun triggerLogin(sourceId: String) {
        val source = extensionLoader.getSource(sourceId) as? AuthenticatedSource ?: return
        cancelPendingValidation(markActiveSourceSignedOut = true)
        _oauthLoginUiState.value = OAuthLoginUiState()
        when (val spec = source.authSpec) {
            AuthSpec.None -> Unit
            is AuthSpec.WebCookie -> {
                sessionManager.beginLogin(sourceId)
                _webLoginUiState.value = WebLoginStateReducer.begin(
                    WebLoginRequest(sourceId, spec, source.webLoginUserAgentOrNull()),
                )
            }
            is AuthSpec.OAuth -> {
                val identity = source.sourceIdentity
                if (identity == null) {
                    _oauthLoginUiState.value = OAuthLoginUiState(errorMessage = OAUTH_UNAVAILABLE_MESSAGE)
                    return
                }
                val launch = runCatching { oauthCoordinator.begin(identity, spec) }
                    .getOrElse {
                        sessionManager.markSignedOut(sourceId)
                        _oauthLoginUiState.value = OAuthLoginUiState(errorMessage = OAUTH_UNAVAILABLE_MESSAGE)
                        return
                    }
                sessionManager.beginLogin(sourceId)
                _oauthLoginUiState.value = OAuthLoginUiState(launch = launch)
            }
        }
    }

    /** Prevents recomposition from launching the external authorization page more than once. */
    fun consumeOAuthLaunch(sourceId: String) {
        if (_oauthLoginUiState.value.launch?.sourceId == sourceId) {
            _oauthLoginUiState.value = OAuthLoginUiState()
        }
    }

    fun reportOAuthLaunchFailure(sourceId: String) {
        oauthCoordinator.cancel(sourceId)
        sessionManager.markSignedOut(sourceId)
        _oauthLoginUiState.value = OAuthLoginUiState(errorMessage = OAUTH_BROWSER_MESSAGE)
    }

    fun consumeOAuthError() {
        _oauthLoginUiState.value = _oauthLoginUiState.value.copy(errorMessage = null)
    }

    /**
     * Starts validation for exactly the request currently displayed by the UI.
     *
     * A parameterless API deliberately prevents a stale WebView callback from validating a
     * replaced request. Repeated taps while validation is in progress are ignored.
     */
    fun completeWebLogin() {
        val request = _webLoginUiState.value.request ?: return
        if (_webLoginUiState.value.isVerifying) return

        val generation = ++loginGeneration
        _webLoginUiState.value = WebLoginStateReducer.beginVerification(_webLoginUiState.value)
        validationJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sessionManager.importWebViewCookies(request.sourceId, request.spec)
                    val source = extensionLoader.getSource(request.sourceId) as? AuthenticatedSource
                    source?.validateSession() == true
                }
            }
            // Preserve coroutine cancellation semantics. In particular, a ViewModel being
            // cleared must not turn into a visible "login failed" state.
            if (result.exceptionOrNull() is CancellationException) return@launch

            // Cancellation may not interrupt every source implementation immediately. Do not
            // allow a late response to overwrite the user's explicit cancellation/retry.
            if (!isCurrentRequest(request, generation)) return@launch

            validationJob = null
            if (result.getOrNull() == true) {
                sessionManager.markSignedIn(request.sourceId)
                _webLoginUiState.value = WebLoginStateReducer.clear()
            } else {
                sessionManager.markExpired(request.sourceId)
                _webLoginUiState.value = WebLoginStateReducer.fail(
                    _webLoginUiState.value,
                    if (result.isFailure) WEB_LOGIN_VERIFICATION_ERROR else WEB_LOGIN_INVALID_MESSAGE,
                )
            }
        }
    }

    /** Cancels verification and removes the login page without allowing a late result to win. */
    fun cancelLogin(sourceId: String) {
        val activeRequest = _webLoginUiState.value.request ?: return
        // A callback from a previous full-screen page must not dismiss a newer login request.
        if (activeRequest.sourceId != sourceId) return

        cancelPendingValidation(markActiveSourceSignedOut = false)
        sessionManager.markSignedOut(sourceId)
    }

    fun logout(sourceId: String) {
        if (_webLoginUiState.value.request?.sourceId == sourceId) {
            cancelLogin(sourceId)
        }
        val source = extensionLoader.getSource(sourceId) as? AuthenticatedSource ?: return
        when (val spec = source.authSpec) {
            AuthSpec.None -> Unit
            is AuthSpec.WebCookie -> viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    sessionManager.logout(sourceId, spec)
                }
            }
            is AuthSpec.OAuth -> {
                source.sourceIdentity?.let(oauthCoordinator::logout)
                oauthCoordinator.cancel(sourceId)
                sessionManager.markSignedOut(sourceId)
            }
        }
    }

    private fun cancelPendingValidation(markActiveSourceSignedOut: Boolean) {
        val activeRequest = _webLoginUiState.value.request
        loginGeneration++
        validationJob?.cancel()
        validationJob = null
        _webLoginUiState.value = WebLoginStateReducer.clear()
        if (markActiveSourceSignedOut && activeRequest != null) {
            sessionManager.markSignedOut(activeRequest.sourceId)
        }
    }

    private fun isCurrentRequest(request: WebLoginRequest, generation: Long): Boolean =
        loginGeneration == generation && _webLoginUiState.value.request == request

    private companion object {
        const val WEB_LOGIN_INVALID_MESSAGE = "登入未完成或登入資訊已失效，請確認後再試一次。"
        const val WEB_LOGIN_VERIFICATION_ERROR = "登入驗證時發生問題，請稍後再試一次。"
        const val OAUTH_UNAVAILABLE_MESSAGE = "此 OAuth 登入尚未在 NewsHub Host 完成設定。"
        const val OAUTH_BROWSER_MESSAGE = "找不到可開啟 OAuth 登入頁面的瀏覽器。"
    }
}
