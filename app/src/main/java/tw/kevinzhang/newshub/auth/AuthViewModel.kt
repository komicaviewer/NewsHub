package tw.kevinzhang.newshub.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val cookieJar: AppCookieJar,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    val loginStatuses: StateFlow<Map<String, LoginStatus>> = authRepository.loginStatuses

    /** Emits a sourceId whenever the host app should launch that extension's LoginActivity. */
    private val _loginRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val loginRequests: SharedFlow<String> = _loginRequests.asSharedFlow()

    fun triggerLogin(sourceId: String) {
        viewModelScope.launch { _loginRequests.emit(sourceId) }
    }

    /**
     * Called after the extension's LoginActivity returns RESULT_OK.
     * Parses raw WebView cookies from the Intent extras and stores them in the shared cookie jar.
     */
    fun onLoginSuccess(sourceId: String, cookieUrl: String?, rawCookies: String?) {
        if (cookieUrl != null && rawCookies != null) {
            cookieJar.addCookiesFromString(cookieUrl, rawCookies)
        }
        authRepository.setLoggedIn(sourceId, cookieUrl ?: "")
    }

    fun logout(sourceId: String) {
        val cookieUrl = authRepository.cookieUrls.value[sourceId]
        if (cookieUrl != null) {
            // Cancel in-flight requests for this domain before clearing cookies.
            val host = runCatching { cookieUrl.toHttpUrl().host }.getOrNull()
            if (host != null) {
                val domain = parentDomain(host)
                val matchesDomain = { call: Call ->
                    val callHost = call.request().url.host
                    callHost == domain || callHost.endsWith(".$domain")
                }
                okHttpClient.dispatcher.runningCalls().filter(matchesDomain).forEach { it.cancel() }
                okHttpClient.dispatcher.queuedCalls().filter(matchesDomain).forEach { it.cancel() }
            }
            cookieJar.clearCookiesForUrl(cookieUrl)
        }
        CookieManager.getInstance().removeAllCookies(null)
        authRepository.logout(sourceId)
    }
}
