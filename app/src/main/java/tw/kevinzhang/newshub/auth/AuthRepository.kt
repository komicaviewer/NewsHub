package tw.kevinzhang.newshub.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

private val LOGIN_STATUS_KEY = stringPreferencesKey("login_status_map_v2")
private val LOGIN_COOKIE_URL_KEY = stringPreferencesKey("login_cookie_url_map_v2")

enum class LoginStatus { NONE, LOGGED_IN }

@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val gson = Gson()

    /** Map of sourceId → LoginStatus, persisted across restarts. */
    val loginStatuses = dataStore.data
        .map { prefs -> deserializeStatus(prefs[LOGIN_STATUS_KEY]) }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())

    /** Map of sourceId → cookieUrl, used to clear the right cookies on logout. */
    val cookieUrls = dataStore.data
        .map { prefs -> deserializeStrings(prefs[LOGIN_COOKIE_URL_KEY]) }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())

    fun setLoggedIn(sourceId: String, cookieUrl: String) {
        applicationScope.launch {
            dataStore.edit { prefs ->
                prefs[LOGIN_STATUS_KEY] = serializeStatus(
                    deserializeStatus(prefs[LOGIN_STATUS_KEY]) + (sourceId to LoginStatus.LOGGED_IN)
                )
                prefs[LOGIN_COOKIE_URL_KEY] = serializeStrings(
                    deserializeStrings(prefs[LOGIN_COOKIE_URL_KEY]) + (sourceId to cookieUrl)
                )
            }
        }
    }

    fun logout(sourceId: String) {
        applicationScope.launch {
            dataStore.edit { prefs ->
                prefs[LOGIN_STATUS_KEY] = serializeStatus(
                    deserializeStatus(prefs[LOGIN_STATUS_KEY]) + (sourceId to LoginStatus.NONE)
                )
            }
        }
    }

    private fun deserializeStatus(json: String?): Map<String, LoginStatus> {
        if (json == null) return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        val raw: Map<String, String> = gson.fromJson(json, type) ?: return emptyMap()
        return raw.mapValues { (_, v) -> runCatching { LoginStatus.valueOf(v) }.getOrDefault(LoginStatus.NONE) }
    }

    private fun serializeStatus(map: Map<String, LoginStatus>): String =
        gson.toJson(map.mapValues { it.value.name })

    private fun deserializeStrings(json: String?): Map<String, String> {
        if (json == null) return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }

    private fun serializeStrings(map: Map<String, String>): String = gson.toJson(map)
}
