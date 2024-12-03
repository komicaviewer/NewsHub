package tw.kevinzhang.newshub.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


private val Context.dataStore by preferencesDataStore("preference_store")

@Singleton
class PreferenceStore @Inject constructor(
    @ApplicationContext appContext: Context
) {
    private object Keys {
        val KEY_OF_DEFAULT_TOPIC = stringPreferencesKey("default_topic")
    }

    private val dataStore = appContext.dataStore

    val observable = dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            mapPreference(preferences)
        }

    private fun mapPreference(preferences: Preferences): Preference {
        return Preference(
            preferences[Keys.KEY_OF_DEFAULT_TOPIC] ?: "",
        )
    }

    data class Preference(
        val defaultTopic: String,
    )
}
