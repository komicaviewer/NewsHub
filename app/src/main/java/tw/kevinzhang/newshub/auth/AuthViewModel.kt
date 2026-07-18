package tw.kevinzhang.newshub.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.AuthenticationRequiredException
import tw.kevinzhang.extension_loader.ExtensionLoader
import javax.inject.Inject

data class WebLoginRequest(
    val sourceId: String,
    val spec: AuthSpec.WebCookie,
)

/** Coordinates authentication requests; it deliberately contains no Android WebView code. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val extensionLoader: ExtensionLoader,
    private val sessionManager: SourceSessionManager,
) : ViewModel() {
    val authStates = sessionManager.states
    private val _webLoginRequests = MutableSharedFlow<WebLoginRequest>(extraBufferCapacity = 1)
    val webLoginRequests: SharedFlow<WebLoginRequest> = _webLoginRequests.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionManager.foregroundLoginRequests.collect(::triggerLogin)
        }
    }

    fun triggerLogin(sourceId: String) {
        val source = extensionLoader.getSource(sourceId) as? AuthenticatedSource ?: return
        val spec = source.authSpec as? AuthSpec.WebCookie ?: return
        sessionManager.beginLogin(sourceId)
        _webLoginRequests.tryEmit(WebLoginRequest(sourceId, spec))
    }

    /** Called only after the in-host WebView has completed a permitted login navigation. */
    fun completeWebLogin(request: WebLoginRequest) {
        viewModelScope.launch {
            sessionManager.importWebViewCookies(request.sourceId, request.spec)
            val source = extensionLoader.getSource(request.sourceId) as? AuthenticatedSource
            val valid = runCatching { source?.validateSession() == true }.getOrDefault(false)
            if (valid) sessionManager.markSignedIn(request.sourceId)
            else sessionManager.markExpired(request.sourceId)
        }
    }

    fun cancelLogin(sourceId: String) = sessionManager.markSignedOut(sourceId)

    fun logout(sourceId: String) {
        val source = extensionLoader.getSource(sourceId) as? AuthenticatedSource ?: return
        val spec = source.authSpec as? AuthSpec.WebCookie ?: return
        sessionManager.logout(sourceId, spec)
    }

    /** Call from a foreground request handler when an extension throws this exception. */
    fun onAuthenticationRequired(sourceId: String, error: AuthenticationRequiredException) {
        sessionManager.markExpired(sourceId)
        if (error.isUserAction) triggerLogin(sourceId)
    }
}
