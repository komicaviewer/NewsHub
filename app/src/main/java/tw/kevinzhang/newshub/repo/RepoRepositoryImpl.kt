package tw.kevinzhang.newshub.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named

private val REPO_URLS_KEY = stringSetPreferencesKey("repo_urls")

class RepoRepositoryImpl @Inject constructor(
    @Named("repoDataStore") private val dataStore: DataStore<Preferences>,
) : RepoRepository {

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
