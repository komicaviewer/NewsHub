package tw.kevinzhang.newshub.auth

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tw.kevinzhang.extension_api.AuthResult
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"

enum class LoginStatus { NONE, LOGGED_IN, FAILED }

data class AuthRequest(val loginUrl: String, val onPageLoadJs: String? = null)

@Singleton
class AuthRepository @Inject constructor() {

    private val _authRequests = MutableSharedFlow<AuthRequest>(extraBufferCapacity = 1)
    val authRequests = _authRequests.asSharedFlow()

    private val _loginStatuses = MutableStateFlow<Map<String, LoginStatus>>(emptyMap())
    val loginStatuses = _loginStatuses.asStateFlow()

    private var pendingResult: CompletableDeferred<AuthResult>? = null
    private var pendingLoginUrl: String? = null

    suspend fun requestAuth(loginUrl: String, onPageLoadJs: String? = null): AuthResult {
        val deferred = CompletableDeferred<AuthResult>()
        pendingResult = deferred
        pendingLoginUrl = loginUrl
        _authRequests.emit(AuthRequest(loginUrl, onPageLoadJs))
        return deferred.await()
    }

    fun completeAuth(result: AuthResult) {
        val loginUrl = pendingLoginUrl ?: return
        val status = when (result) {
            AuthResult.Success -> LoginStatus.LOGGED_IN
            AuthResult.Cancelled -> LoginStatus.FAILED
        }
        _loginStatuses.update { it + (loginUrl to status) }
        Log.d(TAG, "completeAuth loginUrl=$loginUrl status=$status")
        pendingResult?.complete(result)
        pendingResult = null
        pendingLoginUrl = null
    }

    fun logout(loginUrl: String) {
        _loginStatuses.update { it + (loginUrl to LoginStatus.NONE) }
        Log.d(TAG, "logout loginUrl=$loginUrl")
    }
}
