package tw.kevinzhang.newshub.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticationSession
import tw.kevinzhang.extension_api.SourceRuntime
import tw.kevinzhang.extension_api.SourceRuntimeProvider
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val COOKIE_PREFS = "source_cookie_sessions"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "newshub.source.cookies.v1"

@Singleton
class SourceSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseClient: OkHttpClient,
) : SourceRuntimeProvider {
    private val sessions = mutableMapOf<String, SourceSession>()
    private val mutableStates = MutableStateFlow<Map<String, AuthState>>(emptyMap())
    val states: StateFlow<Map<String, AuthState>> = mutableStates.asStateFlow()
    private val mutableForegroundLoginRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val foregroundLoginRequests: SharedFlow<String> = mutableForegroundLoginRequests.asSharedFlow()

    override fun runtimeFor(sourceId: String): SourceRuntime = synchronized(sessions) {
        sessions.getOrPut(sourceId) { SourceSession(sourceId) }.runtime
    }

    fun stateFor(sourceId: String): AuthState = states.value[sourceId] ?: AuthState.Unknown

    fun beginLogin(sourceId: String) = session(sourceId).setState(AuthState.SigningIn)

    fun markSignedIn(sourceId: String) = session(sourceId).setState(AuthState.SignedIn)

    fun markSignedOut(sourceId: String) = session(sourceId).setState(AuthState.SignedOut)

    fun markExpired(sourceId: String) = session(sourceId).setState(AuthState.Expired)

    /** Called by foreground UI request handlers. Background work must only call [markExpired]. */
    fun requestForegroundLogin(sourceId: String) {
        if (stateFor(sourceId) == AuthState.SigningIn) return
        markExpired(sourceId)
        mutableForegroundLoginRequests.tryEmit(sourceId)
    }

    /**
     * Imports only the exact origins declared by the source. CookieManager exposes name/value
     * pairs, so those cookies remain host-only and are never widened to a parent domain.
     */
    fun importWebViewCookies(sourceId: String, spec: AuthSpec.WebCookie) {
        val jar = session(sourceId).jar
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        spec.validCookieOrigins().forEach { origin ->
            val url = origin.toHttpsUrlOrNull() ?: return@forEach
            val raw = cookieManager.getCookie(url.toString()) ?: return@forEach
            val cookies = raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
            if (cookies.isNotEmpty()) jar.saveFromResponse(url, cookies)
        }
    }

    /** Clears only one source's OkHttp and WebView cookies. */
    fun logout(sourceId: String, spec: AuthSpec.WebCookie) {
        val jar = session(sourceId).jar
        val cookieManager = CookieManager.getInstance()
        spec.validCookieOrigins().forEach { origin ->
            val url = origin.toHttpsUrlOrNull() ?: return@forEach
            val cookieNames = buildSet {
                addAll(jar.cookiesFor(url).map { it.name })
                cookieManager.getCookie(url.toString())
                    ?.split(';')
                    ?.mapTo(this) { it.substringBefore('=').trim() }
            }
            cookieNames.forEach { name ->
                cookieManager.setCookie(url.toString(), "$name=; Max-Age=0; Path=/")
                spec.cookieDomains
                    .filter { domain -> url.host == domain || url.host.endsWith(".$domain") }
                    .forEach { domain ->
                        cookieManager.setCookie(url.toString(), "$name=; Max-Age=0; Domain=$domain; Path=/")
                    }
            }
        }
        cookieManager.flush()
        jar.clear()
        session(sourceId).setState(AuthState.SignedOut)
    }

    private fun session(sourceId: String): SourceSession = synchronized(sessions) {
        sessions.getOrPut(sourceId) { SourceSession(sourceId) }
    }

    private inner class SourceSession(private val sourceId: String) {
        val jar = SourceCookieJar(context, sourceId)
        private val mutableState = MutableStateFlow(if (jar.isEmpty()) AuthState.SignedOut else AuthState.Unknown)

        init {
            mutableStates.value = mutableStates.value + (sourceId to mutableState.value)
        }

        val runtime = object : SourceRuntime {
            override val httpClient: OkHttpClient = baseClient.newBuilder().cookieJar(jar).build()
            override val authentication: AuthenticationSession = object : AuthenticationSession {
                override val state: StateFlow<AuthState> = mutableState.asStateFlow()
                override fun markExpired() = setState(AuthState.Expired)
            }
        }

        fun setState(state: AuthState) {
            mutableState.value = state
            mutableStates.value = mutableStates.value + (sourceId to state)
        }
    }
}

private fun String.toHttpsUrlOrNull(): HttpUrl? = runCatching {
    toHttpUrl().takeIf { it.isHttps }
}.getOrNull()

private fun AuthSpec.WebCookie.validCookieOrigins(): Set<String> = cookieOrigins.filterTo(mutableSetOf()) { origin ->
    val host = origin.toHttpsUrlOrNull()?.host ?: return@filterTo false
    host in allowedHosts.map { it.lowercase() }
}

/** Cookie jar isolated by source id. Android 23+ cookies are AES-GCM encrypted at rest. */
internal class SourceCookieJar(context: Context, private val sourceId: String) : CookieJar {
    private data class StoredCookie(
        val name: String, val value: String, val domain: String, val path: String,
        val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean, val expiresAt: Long,
    )

    private val prefs = context.getSharedPreferences(COOKIE_PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cookies = mutableListOf<Cookie>()

    init { load() }

    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) = synchronized(cookies) {
        newCookies.forEach { incoming ->
            // RFC cookie identity includes path. Do not collapse same-name cookies from paths.
            cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt >= System.currentTimeMillis()) cookies += incoming
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        val now = System.currentTimeMillis()
        val expired = cookies.removeAll { it.expiresAt != Long.MAX_VALUE && it.expiresAt <= now }
        if (expired) persist()
        cookies.filter { it.matches(url) }
    }

    fun cookiesFor(url: HttpUrl): List<Cookie> = loadForRequest(url)
    fun isEmpty(): Boolean = synchronized(cookies) { cookies.isEmpty() }
    fun clear() = synchronized(cookies) { cookies.clear(); persist() }

    private fun load() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val encrypted = prefs.getString(sourceId, null) ?: return
        val json = runCatching { decrypt(encrypted) }.getOrNull() ?: return
        val type = object : TypeToken<List<StoredCookie>>() {}.type
        val now = System.currentTimeMillis()
        val loaded: List<StoredCookie> = gson.fromJson(json, type) ?: emptyList()
        cookies += loaded.filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }.mapNotNull { it.toCookie() }
    }

    private fun persist() {
        // API 21/22 intentionally keeps login material memory-only: no plaintext fallback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val stored = cookies.map { it.toStored() }
        val encrypted = runCatching { encrypt(gson.toJson(stored)) }.getOrNull() ?: return
        prefs.edit().putString(sourceId, encrypted).apply()
    }

    private fun StoredCookie.toCookie(): Cookie? = runCatching {
        Cookie.Builder().name(name).value(value).apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            path(path)
            if (secure) secure()
            if (httpOnly) httpOnly()
            if (expiresAt != Long.MAX_VALUE) expiresAt(expiresAt)
        }.build()
    }.getOrNull()

    private fun Cookie.toStored() = StoredCookie(name, value, domain, path, secure, httpOnly, hostOnly, expiresAt)

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val all = Base64.decode(value, Base64.NO_WRAP)
        require(all.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        return String(cipher.doFinal(all.copyOfRange(12, all.size)), StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
