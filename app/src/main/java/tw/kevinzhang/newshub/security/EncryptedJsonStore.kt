package tw.kevinzhang.newshub.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ENCRYPTED_PAYLOAD_KEY = "payload"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val GCM_IV_LENGTH_BYTES = 12

/** Android Keystore AES-GCM storage. Plaintext fallback is deliberately unsupported. */
internal class EncryptedJsonStore(
    context: Context,
    prefsName: String,
    private val keyAlias: String,
    private val errorSubject: String,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun read(): String? {
        val encoded = prefs.getString(ENCRYPTED_PAYLOAD_KEY, null) ?: return null
        return decrypt(encoded)
    }

    fun write(plainText: String) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            "Encrypted $errorSubject storage is unavailable"
        }
        check(prefs.edit().putString(ENCRYPTED_PAYLOAD_KEY, encrypt(plainText)).commit()) {
            "Could not persist encrypted $errorSubject state"
        }
    }

    fun clear() {
        check(prefs.edit().remove(ENCRYPTED_PAYLOAD_KEY).commit()) {
            "Could not clear encrypted $errorSubject state"
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val encrypted = Base64.decode(value, Base64.NO_WRAP)
        require(encrypted.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted $errorSubject payload" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, encrypted.copyOfRange(0, GCM_IV_LENGTH_BYTES)),
        )
        return String(
            cipher.doFinal(encrypted.copyOfRange(GCM_IV_LENGTH_BYTES, encrypted.size)),
            StandardCharsets.UTF_8,
        )
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
