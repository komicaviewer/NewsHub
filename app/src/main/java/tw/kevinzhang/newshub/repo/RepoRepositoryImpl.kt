package tw.kevinzhang.newshub.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Named

private val REPO_URLS_KEY = stringSetPreferencesKey("repo_urls")
private const val DEFAULT_REPO_URL = "https://github.com/komicaviewer/extensions"

class RepoRepositoryImpl @Inject constructor(
    @Named("repoDataStore") private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : RepoRepository {

    init {
        applicationScope.launch {
            val current = dataStore.data.first()[REPO_URLS_KEY]
            if (current.isNullOrEmpty()) {
                dataStore.edit { prefs ->
                    prefs[REPO_URLS_KEY] = setOf(DEFAULT_REPO_URL)
                }
            }
        }
    }

    override fun getRepoUrls(): Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[REPO_URLS_KEY] ?: emptySet() }

    override suspend fun addRepoUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[REPO_URLS_KEY] = (prefs[REPO_URLS_KEY] ?: emptySet()) + url.trim().trimEnd('/')
        }
    }

    override suspend fun removeRepoUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[REPO_URLS_KEY] = (prefs[REPO_URLS_KEY] ?: emptySet()) - url
        }
    }
}
