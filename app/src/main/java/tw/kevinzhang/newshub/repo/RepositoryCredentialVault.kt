package tw.kevinzhang.newshub.repo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.marketplace.RepositoryCredentialStore
import tw.kevinzhang.newshub.security.EncryptedJsonStore

private const val REPOSITORY_CREDENTIAL_PREFS = "repository_credentials_v1"
private const val REPOSITORY_CREDENTIAL_KEY_ALIAS = "newshub.repository.credentials.v1"

/** Repository credentials encrypted with an app-private Android Keystore AES-GCM key. */
@Singleton
class RepositoryCredentialVault @Inject constructor(
    @ApplicationContext context: Context,
) : RepositoryCredentialStore {
    private val lock = Any()
    private val store = EncryptedJsonStore(
        context,
        REPOSITORY_CREDENTIAL_PREFS,
        REPOSITORY_CREDENTIAL_KEY_ALIAS,
        errorSubject = "repository credential",
    )
    private val type = object : TypeToken<Map<String, String>>() {}.type

    override suspend fun getCredential(domainId: String): RepositoryAccessCredential? =
        synchronized(lock) { readAll()[domainId]?.let(RepositoryAccessCredential::githubToken) }

    override suspend fun saveCredential(
        domainId: String,
        credential: RepositoryAccessCredential,
    ) = synchronized(lock) {
        val updated = readAll().toMutableMap()
        credential.withSecret { token -> updated[domainId] = token }
        store.write(Gson().toJson(updated, type))
    }

    override suspend fun deleteCredential(domainId: String) = synchronized(lock) {
        val updated = readAll().toMutableMap().apply { remove(domainId) }
        if (updated.isEmpty()) store.clear() else store.write(Gson().toJson(updated, type))
    }

    private fun readAll(): Map<String, String> = runCatching(store::read).getOrElse {
        store.clear()
        null
    }?.let { json ->
        runCatching { Gson().fromJson<Map<String, String>>(json, type) }
            .getOrElse {
                // Corrupt or undecryptable credential state must fail closed, never downgrade to
                // plaintext or keep partially readable credentials.
                store.clear()
                emptyMap()
            }
    } ?: emptyMap()
}
