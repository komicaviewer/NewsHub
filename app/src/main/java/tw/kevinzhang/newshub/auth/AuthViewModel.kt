package tw.kevinzhang.newshub.auth

import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.AuthResult
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val cookieJar: AppCookieJar,
) : ViewModel() {
    val authRequests: SharedFlow<AuthRequest> = authRepository.authRequests
    val loginStatuses: StateFlow<Map<String, LoginStatus>> = authRepository.loginStatuses

    fun completeAuth(result: AuthResult) = authRepository.completeAuth(result)

    fun triggerManualLogin(loginUrl: String, onPageLoadJs: String? = null) {
        viewModelScope.launch { authRepository.requestAuth(loginUrl, onPageLoadJs) }
    }

    fun logout(loginUrl: String) {
        authRepository.logout(loginUrl)
        cookieJar.clearCookiesForUrl(loginUrl)
        CookieManager.getInstance().removeAllCookies(null)
    }
}
