package tw.kevinzhang.newshub.auth.oauth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import tw.kevinzhang.newshub.security.EncryptedJsonStore

private const val OAUTH_TOKEN_PREFS = "oauth_token_vault_v1"
private const val OAUTH_TOKEN_KEY_ALIAS = "newshub.oauth.tokens.v1"
private const val OAUTH_TRANSACTION_PREFS = "oauth_transactions_v1"
private const val OAUTH_TRANSACTION_KEY_ALIAS = "newshub.oauth.transactions.v1"

@Singleton
class OAuthTokenVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = EncryptedJsonStore(
        context,
        OAUTH_TOKEN_PREFS,
        OAUTH_TOKEN_KEY_ALIAS,
        errorSubject = "OAuth token",
    )
    private val type = object : TypeToken<Map<String, StoredOAuthCredential>>() {}.type

    @Synchronized
    fun credential(sourceStorageKey: String): StoredOAuthCredential? = readAll()[sourceStorageKey]

    @Synchronized
    fun put(credential: StoredOAuthCredential) {
        val updated = readAll().toMutableMap().apply { put(credential.sourceStorageKey, credential) }
        store.write(Gson().toJson(updated, type))
    }

    @Synchronized
    fun remove(sourceStorageKey: String) {
        val updated = readAll().toMutableMap().apply { remove(sourceStorageKey) }
        if (updated.isEmpty()) store.clear() else store.write(Gson().toJson(updated, type))
    }

    private fun readAll(): Map<String, StoredOAuthCredential> = store.read()?.let { json ->
        runCatching { Gson().fromJson<Map<String, StoredOAuthCredential>>(json, type) }
            .getOrElse {
                store.clear()
                emptyMap()
            }
    } ?: emptyMap()
}

@Singleton
class OAuthTransactionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = EncryptedJsonStore(
        context,
        OAUTH_TRANSACTION_PREFS,
        OAUTH_TRANSACTION_KEY_ALIAS,
        errorSubject = "OAuth transaction",
    )
    private val type = object : TypeToken<Map<String, OAuthTransaction>>() {}.type

    @Synchronized
    fun put(transaction: OAuthTransaction, nowEpochMillis: Long = System.currentTimeMillis()) {
        val updated = readAll()
            .filterValues { it.expiresAtEpochMillis > nowEpochMillis }
            .toMutableMap()
            .apply {
                entries.removeAll { it.value.identity.sourceId == transaction.identity.sourceId }
                put(transaction.state, transaction)
            }
        store.write(Gson().toJson(updated, type))
    }

    /** A state is removed before token exchange so a callback can never be replayed. */
    @Synchronized
    fun consume(state: String, nowEpochMillis: Long = System.currentTimeMillis()): OAuthTransaction? {
        val all = readAll().toMutableMap()
        val matchedState = all.keys.firstOrNull { stored -> OAuthSecurity.constantTimeEquals(stored, state) }
        val transaction = matchedState?.let(all::remove)?.takeIf { it.expiresAtEpochMillis > nowEpochMillis }
        val current = all.filterValues { it.expiresAtEpochMillis > nowEpochMillis }
        if (current.isEmpty()) store.clear() else store.write(Gson().toJson(current, type))
        return transaction
    }

    @Synchronized
    fun removeSource(sourceId: String) {
        val current = readAll().filterValues { it.identity.sourceId != sourceId }
        if (current.isEmpty()) store.clear() else store.write(Gson().toJson(current, type))
    }

    private fun readAll(): Map<String, OAuthTransaction> = store.read()?.let { json ->
        runCatching { Gson().fromJson<Map<String, OAuthTransaction>>(json, type) }
            .getOrElse {
                store.clear()
                emptyMap()
            }
    } ?: emptyMap()
}
