package tw.kevinzhang.newshub.auth

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.AuthResult
import tw.kevinzhang.newshub.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"
private val LOGIN_STATUS_KEY = stringPreferencesKey("login_status_map")

enum class LoginStatus { NONE, LOGGED_IN, FAILED }

data class AuthRequest(val loginUrl: String, val onPageLoadJs: String? = null)

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    private val gson = Gson()

    private val _authRequests = MutableSharedFlow<AuthRequest>(extraBufferCapacity = 1)
    val authRequests = _authRequests.asSharedFlow()

    val loginStatuses = dataStore.data
        .map { prefs -> deserialize(prefs[LOGIN_STATUS_KEY]) }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())

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
        Log.d(TAG, "completeAuth loginUrl=$loginUrl status=$status")
        applicationScope.launch {
            dataStore.edit { prefs ->
                val current = deserialize(prefs[LOGIN_STATUS_KEY])
                prefs[LOGIN_STATUS_KEY] = serialize(current + (loginUrl to status))
            }
        }
        pendingResult?.complete(result)
        pendingResult = null
        pendingLoginUrl = null
    }

    fun restoreLoginStatus(loginUrl: String) {
        applicationScope.launch {
            dataStore.edit { prefs ->
                val current = deserialize(prefs[LOGIN_STATUS_KEY])
                if (current[loginUrl] == null) {
                    prefs[LOGIN_STATUS_KEY] = serialize(current + (loginUrl to LoginStatus.LOGGED_IN))
                }
            }
        }
    }

    fun logout(loginUrl: String) {
        Log.d(TAG, "logout loginUrl=$loginUrl")
        applicationScope.launch {
            dataStore.edit { prefs ->
                val current = deserialize(prefs[LOGIN_STATUS_KEY])
                prefs[LOGIN_STATUS_KEY] = serialize(current + (loginUrl to LoginStatus.NONE))
            }
        }
    }

    private fun deserialize(json: String?): Map<String, LoginStatus> {
        if (json == null) return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        val raw: Map<String, String> = gson.fromJson(json, type) ?: return emptyMap()
        return raw.mapValues { (_, v) -> runCatching { LoginStatus.valueOf(v) }.getOrDefault(LoginStatus.NONE) }
    }

    private fun serialize(map: Map<String, LoginStatus>): String =
        gson.toJson(map.mapValues { it.value.name })
}
