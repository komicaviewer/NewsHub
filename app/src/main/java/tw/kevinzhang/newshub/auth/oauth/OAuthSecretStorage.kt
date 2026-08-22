package tw.kevinzhang.newshub.auth.oauth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val OAUTH_TOKEN_PREFS = "oauth_token_vault_v1"
private const val OAUTH_TOKEN_KEY_ALIAS = "newshub.oauth.tokens.v1"
private const val OAUTH_TRANSACTION_PREFS = "oauth_transactions_v1"
private const val OAUTH_TRANSACTION_KEY_ALIAS = "newshub.oauth.transactions.v1"
private const val ENCRYPTED_PAYLOAD_KEY = "payload"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

@Singleton
class OAuthTokenVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = EncryptedJsonStore(context, OAUTH_TOKEN_PREFS, OAUTH_TOKEN_KEY_ALIAS)
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
    private val store = EncryptedJsonStore(context, OAUTH_TRANSACTION_PREFS, OAUTH_TRANSACTION_KEY_ALIAS)
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

/** Android Keystore AES-GCM; there is deliberately no plaintext compatibility fallback. */
private class EncryptedJsonStore(
    context: Context,
    prefsName: String,
    private val keyAlias: String,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun read(): String? {
        val encoded = prefs.getString(ENCRYPTED_PAYLOAD_KEY, null) ?: return null
        return decrypt(encoded)
    }

    fun write(plainText: String) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { "Encrypted OAuth storage is unavailable" }
        check(prefs.edit().putString(ENCRYPTED_PAYLOAD_KEY, encrypt(plainText)).commit()) {
            "Could not persist encrypted OAuth state"
        }
    }

    fun clear() {
        check(prefs.edit().remove(ENCRYPTED_PAYLOAD_KEY).commit()) { "Could not clear OAuth state" }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val encrypted = Base64.decode(value, Base64.NO_WRAP)
        require(encrypted.size > 12) { "Invalid encrypted OAuth payload" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
        )
        return String(cipher.doFinal(encrypted.copyOfRange(12, encrypted.size)), StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }
}
