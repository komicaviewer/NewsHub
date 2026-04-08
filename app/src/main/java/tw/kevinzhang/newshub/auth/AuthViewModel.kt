package tw.kevinzhang.newshub.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tw.kevinzhang.extension_api.AuthResult
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val cookieJar: AppCookieJar,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {
    val authRequests: SharedFlow<AuthRequest> = authRepository.authRequests
    val loginStatuses: StateFlow<Map<String, LoginStatus>> = authRepository.loginStatuses

    fun completeAuth(result: AuthResult) = authRepository.completeAuth(result)

    fun triggerManualLogin(loginUrl: String, onPageLoadJs: String? = null) {
        viewModelScope.launch { authRepository.requestAuth(loginUrl, onPageLoadJs) }
    }

    fun logout(loginUrl: String) {
        // Cancel in-flight & queued requests for this domain before clearing cookies,
        // to prevent saveFromResponse from re-adding cookies after the clear.
        val host = runCatching { loginUrl.toHttpUrl().host }.getOrNull()
        if (host != null) {
            val domain = parentDomain(host)
            val matchesDomain = { call: Call ->
                val callHost = call.request().url.host
                callHost == domain || callHost.endsWith(".$domain")
            }
            okHttpClient.dispatcher.runningCalls().filter(matchesDomain).forEach { it.cancel() }
            okHttpClient.dispatcher.queuedCalls().filter(matchesDomain).forEach { it.cancel() }
        }
        cookieJar.clearCookiesForUrl(loginUrl)
        authRepository.logout(loginUrl)
        CookieManager.getInstance().removeAllCookies(null)
    }
}
