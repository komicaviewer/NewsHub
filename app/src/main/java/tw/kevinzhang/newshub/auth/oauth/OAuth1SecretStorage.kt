package tw.kevinzhang.newshub.auth.oauth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import tw.kevinzhang.newshub.security.EncryptedJsonStore

private const val OAUTH1_TOKEN_PREFS = "oauth1_token_vault_v1"
private const val OAUTH1_TOKEN_KEY_ALIAS = "newshub.oauth1.tokens.v1"
private const val OAUTH1_TRANSACTION_PREFS = "oauth1_transactions_v1"
private const val OAUTH1_TRANSACTION_KEY_ALIAS = "newshub.oauth1.transactions.v1"

@Singleton
class OAuth1TokenVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = EncryptedJsonStore(
        context,
        OAUTH1_TOKEN_PREFS,
        OAUTH1_TOKEN_KEY_ALIAS,
        errorSubject = "OAuth 1 token",
    )
    private val type = object : TypeToken<Map<String, StoredOAuth1Credential>>() {}.type

    @Synchronized
    fun credential(sourceStorageKey: String): StoredOAuth1Credential? = readAll()[sourceStorageKey]

    @Synchronized
    fun put(credential: StoredOAuth1Credential) {
        val updated = readAll().toMutableMap().apply { put(credential.sourceStorageKey, credential) }
        store.write(Gson().toJson(updated, type))
    }

    @Synchronized
    fun remove(sourceStorageKey: String) {
        val updated = readAll().toMutableMap().apply { remove(sourceStorageKey) }
        if (updated.isEmpty()) store.clear() else store.write(Gson().toJson(updated, type))
    }

    private fun readAll(): Map<String, StoredOAuth1Credential> = store.read()?.let { json ->
        runCatching { Gson().fromJson<Map<String, StoredOAuth1Credential>>(json, type) }
            .getOrElse {
                store.clear()
                emptyMap()
            }
    } ?: emptyMap()
}

@Singleton
class OAuth1TransactionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = EncryptedJsonStore(
        context,
        OAUTH1_TRANSACTION_PREFS,
        OAUTH1_TRANSACTION_KEY_ALIAS,
        errorSubject = "OAuth 1 transaction",
    )
    private val type = object : TypeToken<Map<String, OAuth1Transaction>>() {}.type

    @Synchronized
    fun put(transaction: OAuth1Transaction, nowEpochMillis: Long = System.currentTimeMillis()) {
        val current = readAll()
            .filterValues { it.expiresAtEpochMillis > nowEpochMillis }
            .toMutableMap()
            .apply {
                entries.removeAll { it.value.identity.sourceId == transaction.identity.sourceId }
                put(transaction.requestToken, transaction)
            }
        store.write(Gson().toJson(current, type))
    }

    @Synchronized
    fun hasCallback(callbackUri: String, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        readAll().values.any { transaction ->
            transaction.expiresAtEpochMillis > nowEpochMillis &&
                oauth1CallbackMatches(callbackUri, transaction.callbackUri)
        }

    /** Removes the request-token secret before exchange so a callback cannot be replayed. */
    @Synchronized
    fun consume(requestToken: String, nowEpochMillis: Long = System.currentTimeMillis()): OAuth1Transaction? {
        val all = readAll().toMutableMap()
        val matched = all.keys.firstOrNull { OAuthSecurity.constantTimeEquals(it, requestToken) }
        val transaction = matched?.let(all::remove)?.takeIf { it.expiresAtEpochMillis > nowEpochMillis }
        val current = all.filterValues { it.expiresAtEpochMillis > nowEpochMillis }
        if (current.isEmpty()) store.clear() else store.write(Gson().toJson(current, type))
        return transaction
    }

    /** Consumes an unambiguous pending transaction when the provider returns a denial callback. */
    @Synchronized
    fun consumeCallback(callbackUri: String, nowEpochMillis: Long = System.currentTimeMillis()): OAuth1Transaction? {
        val all = readAll().toMutableMap()
        val current = all.filterValues { it.expiresAtEpochMillis > nowEpochMillis }
        val matches = current.values.filter { oauth1CallbackMatches(callbackUri, it.callbackUri) }
        val transaction = matches.singleOrNull()
        transaction?.let { all.remove(it.requestToken) }
        val remaining = all.filterValues { it.expiresAtEpochMillis > nowEpochMillis }
        if (remaining.isEmpty()) store.clear() else store.write(Gson().toJson(remaining, type))
        return transaction
    }

    @Synchronized
    fun removeSource(sourceId: String) {
        val current = readAll().filterValues { it.identity.sourceId != sourceId }
        if (current.isEmpty()) store.clear() else store.write(Gson().toJson(current, type))
    }

    private fun readAll(): Map<String, OAuth1Transaction> = store.read()?.let { json ->
        runCatching { Gson().fromJson<Map<String, OAuth1Transaction>>(json, type) }
            .getOrElse {
                store.clear()
                emptyMap()
            }
    } ?: emptyMap()
}
