package tw.kevinzhang.newshub.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        val KEY_DEFAULT_COLLECTION_ID = stringPreferencesKey("default_collection_id")
        val KEY_TIMELINE_DISPLAY_MODE = stringPreferencesKey("timeline_display_mode")
        val KEY_REPLY_DISPLAY_MODE = stringPreferencesKey("reply_display_mode")
        val KEY_READ_TRACKING_MODE = stringPreferencesKey("read_tracking_mode")
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

    suspend fun setDefaultCollectionId(id: String) {
        dataStore.edit { prefs ->
            prefs[Keys.KEY_DEFAULT_COLLECTION_ID] = id
        }
    }

    suspend fun setTimelineDisplayMode(mode: TimelineDisplayMode) {
        dataStore.edit { prefs ->
            prefs[Keys.KEY_TIMELINE_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun setReplyDisplayMode(mode: ReplyDisplayMode) {
        dataStore.edit { prefs ->
            prefs[Keys.KEY_REPLY_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun setReadTrackingMode(mode: ReadTrackingMode) {
        dataStore.edit { prefs ->
            prefs[Keys.KEY_READ_TRACKING_MODE] = mode.name
        }
    }

    /**
     * The selected source is scoped to a collection instead of being a global timeline setting.
     * A missing value represents the "all sources" filter.
     */
    fun observeCollectionSelectedSourceId(collectionId: String) = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[collectionSelectedSourceKey(collectionId)] }

    suspend fun setCollectionSelectedSourceId(collectionId: String, sourceId: String?) {
        dataStore.edit { prefs ->
            val key = collectionSelectedSourceKey(collectionId)
            if (sourceId == null) {
                prefs.remove(key)
            } else {
                prefs[key] = sourceId
            }
        }
    }

    private fun collectionSelectedSourceKey(collectionId: String) =
        stringPreferencesKey("collection_selected_source_$collectionId")

    private fun mapPreference(preferences: Preferences): Preference {
        return Preference(
            defaultCollectionId = preferences[Keys.KEY_DEFAULT_COLLECTION_ID],
            readingPreferences = ReadingPreferences(
                timelineDisplayMode = TimelineDisplayMode.fromStoredValue(
                    preferences[Keys.KEY_TIMELINE_DISPLAY_MODE],
                ),
                replyDisplayMode = ReplyDisplayMode.fromStoredValue(
                    preferences[Keys.KEY_REPLY_DISPLAY_MODE],
                ),
                readTrackingMode = ReadTrackingMode.fromStoredValue(
                    preferences[Keys.KEY_READ_TRACKING_MODE],
                ),
            ),
        )
    }

    data class Preference(
        val defaultCollectionId: String?,
        val readingPreferences: ReadingPreferences = ReadingPreferences(),
    ) {
        val timelineDisplayMode: TimelineDisplayMode
            get() = readingPreferences.timelineDisplayMode

        val replyDisplayMode: ReplyDisplayMode
            get() = readingPreferences.replyDisplayMode

        val readTrackingMode: ReadTrackingMode
            get() = readingPreferences.readTrackingMode
    }
}
