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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tw.kevinzhang.extension_api.AuthState
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.HostBrokerProvider
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.IHostBroker
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.ResourceHandle
import tw.kevinzhang.extension_api.ResourcePayload
import tw.kevinzhang.extension_api.ResourceRange
import tw.kevinzhang.extension_api.ExternalLinkHandle
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperations
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val COOKIE_PREFS = "source_cookie_sessions"
private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "newshub.source.cookies.v1"
private const val MAX_RESOURCE_HANDLES_PER_SOURCE = 4_096
private const val MAX_LINK_HANDLES_PER_SOURCE = 1_024

@Singleton
class SourceSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseClient: OkHttpClient,
) : HostBrokerProvider, HostResourceProvider {
    private val sessions = mutableMapOf<String, SourceSession>()
    private val sourceIdentities = mutableMapOf<String, String>()
    private val resourceGenerations = mutableMapOf<String, Long>()
    private val resourceBrokers = mutableMapOf<String, SourceNetworkBroker>()
    private val resources = mutableMapOf<String, ResourceBinding>()
    private val externalLinks = mutableMapOf<String, ExternalLinkBinding>()
    private val mutableStates = MutableStateFlow<Map<String, AuthState>>(emptyMap())
    val states: StateFlow<Map<String, AuthState>> = mutableStates.asStateFlow()
    private val mutableAuthenticationRequiredNotice = MutableStateFlow<String?>(null)
    /**
     * A pending foreground notice. It is stateful so an authentication failure raised during
     * initial loading is not lost before the screen's Compose collector has started.
     */
    val authenticationRequiredNotice: StateFlow<String?> = mutableAuthenticationRequiredNotice.asStateFlow()

    override fun brokerFor(identity: SourceIdentity, policy: SourceNetworkPolicy): IHostBroker {
        val storageKey = identity.storageKey()
        synchronized(sessions) {
            sourceIdentities[identity.sourceId] = storageKey
        }
        val broker = SourceNetworkBroker(baseClient, session(identity.sourceId).jar, identity, policy)
        synchronized(resources) {
            resourceBrokers.remove(storageKey)?.revoke()
            val generation = (resourceGenerations[storageKey] ?: 0L) + 1L
            resourceGenerations[storageKey] = generation
            resourceBrokers[storageKey] = broker
            resources.entries.removeAll { it.value.storageKey == storageKey }
            externalLinks.entries.removeAll { it.value.storageKey == storageKey }
        }
        return broker
    }

    override fun issueResource(
        identity: SourceIdentity,
        policy: SourceNetworkPolicy,
        untrustedUrl: String,
    ): ResourceHandle {
        require(NamedHostCapabilities.RESOURCE_READ in policy.namedCapabilities) {
            "Resource capability is not authorized"
        }
        // Reuse the exact network authorization boundary before issuing an opaque UI capability.
        validateSourceNetworkRequest(
            tw.kevinzhang.extension_api.SourceNetworkRequest(
                NetworkOperations.SOURCE_READ,
                "GET",
                untrustedUrl,
            ),
            policy,
        )
        val storageKey = identity.storageKey()
        return synchronized(resources) {
            val generation = requireNotNull(resourceGenerations[storageKey]) { "Source session is not active" }
            require(resourceBrokers.containsKey(storageKey)) { "Source broker is not active" }
            require(resources.values.count { it.storageKey == storageKey } < MAX_RESOURCE_HANDLES_PER_SOURCE) {
                "Source issued too many resource handles"
            }
            val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val token = Base64.encodeToString(tokenBytes, Base64.NO_WRAP or Base64.URL_SAFE).trimEnd('=')
            val handle = ResourceHandle(storageKey.take(16), generation, token)
            resources[token] = ResourceBinding(storageKey, generation, untrustedUrl)
            handle
        }
    }

    override suspend fun openResource(handle: ResourceHandle): ResourcePayload {
        val (binding, broker) = synchronized(resources) {
            val binding = requireNotNull(resources[handle.token]) { "Unknown resource handle" }
            require(binding.storageKey.take(16) == handle.sourceSession) { "Resource source mismatch" }
            require(binding.generation == handle.generation) { "Stale resource handle" }
            require(resourceGenerations[binding.storageKey] == handle.generation) { "Revoked resource handle" }
            binding to requireNotNull(resourceBrokers[binding.storageKey]) { "Source broker is unavailable" }
        }
        val payload = broker.fetchResource(binding.url)
        ensureResourceCurrent(handle, binding)
        return payload
    }

    override suspend fun openResourceRange(
        handle: ResourceHandle,
        offset: Long,
        length: Int,
    ): ResourceRange {
        val (binding, broker) = resolveResource(handle)
        val range = broker.fetchResourceRange(binding.url, offset, length)
        ensureResourceCurrent(handle, binding)
        return range
    }

    override fun issueExternalLink(
        identity: SourceIdentity,
        policy: SourceNetworkPolicy,
        untrustedUrl: String,
    ): ExternalLinkHandle {
        require(NamedHostCapabilities.EXTERNAL_LINK in policy.namedCapabilities) {
            "External link capability is not authorized"
        }
        validateExternalLink(untrustedUrl, policy)
        val storageKey = identity.storageKey()
        return synchronized(resources) {
            val generation = requireNotNull(resourceGenerations[storageKey]) { "Source session is not active" }
            require(resourceBrokers.containsKey(storageKey)) { "Source broker is not active" }
            require(externalLinks.values.count { it.storageKey == storageKey } < MAX_LINK_HANDLES_PER_SOURCE) {
                "Source issued too many external link handles"
            }
            val token = secureCapabilityToken()
            val handle = ExternalLinkHandle(storageKey.take(16), generation, token)
            externalLinks[token] = ExternalLinkBinding(storageKey, generation, untrustedUrl)
            handle
        }
    }

    override fun consumeExternalLink(handle: ExternalLinkHandle): String = synchronized(resources) {
        val binding = requireNotNull(externalLinks[handle.token]) { "Unknown external link handle" }
        require(binding.storageKey.take(16) == handle.sourceSession) { "External link source mismatch" }
        require(binding.generation == handle.generation) { "Stale external link handle" }
        require(resourceGenerations[binding.storageKey] == handle.generation) { "Revoked external link handle" }
        require(resourceBrokers.containsKey(binding.storageKey)) { "Source broker is unavailable" }
        externalLinks.remove(handle.token)
        binding.url
    }

    override fun revoke(identity: SourceIdentity) {
        val storageKey = identity.storageKey()
        synchronized(resources) {
            resourceBrokers.remove(storageKey)?.revoke()
            rotateHandles(storageKey)
        }
    }

    fun stateFor(sourceId: String): AuthState = states.value[sourceId] ?: AuthState.Unknown

    fun beginLogin(sourceId: String) {
        invalidateSourceCapabilities(sourceId)
        session(sourceId).setState(AuthState.SigningIn)
    }

    fun markSignedIn(sourceId: String) {
        invalidateSourceCapabilities(sourceId)
        session(sourceId).setState(AuthState.SignedIn)
    }

    fun markSignedOut(sourceId: String) {
        invalidateSourceCapabilities(sourceId)
        session(sourceId).setState(AuthState.SignedOut)
    }

    fun markExpired(sourceId: String) {
        invalidateSourceCapabilities(sourceId)
        session(sourceId).setState(AuthState.Expired)
    }

    /** Imports a pre-validated Host snapshot before any extension service is bound. */
    fun importHostOwnedSession(
        identity: SourceIdentity,
        cookies: List<Cookie>,
    ) {
        require(cookies.isNotEmpty()) { "Host session import is empty" }
        val storageKey = identity.storageKey()
        synchronized(sessions) {
            require(sourceIdentities[identity.sourceId] == null || sourceIdentities[identity.sourceId] == storageKey) {
                "Host session identity mismatch"
            }
            sourceIdentities[identity.sourceId] = storageKey
        }
        session(identity.sourceId).apply {
            jar.saveAllFromResponses(cookies)
            setState(AuthState.Unknown)
        }
    }

    /**
     * Called when a foreground request reaches a protected resource. The user explicitly starts
     * login later from Boards, so this only expires the source session and emits a notice.
     */
    fun notifyAuthenticationRequired(sourceId: String) {
        markExpired(sourceId)
        // Keep notifying on later foreground attempts: a previous snackbar may have expired
        // before the user could act on it.
        mutableAuthenticationRequiredNotice.value = sourceId
    }

    /** Clears a notice only when it is still for the source that the screen displayed. */
    fun consumeAuthenticationRequiredNotice(sourceId: String) {
        if (mutableAuthenticationRequiredNotice.value == sourceId) {
            mutableAuthenticationRequiredNotice.value = null
        }
    }

    /**
     * Imports only the exact origins declared by the source. CookieManager exposes name/value
     * pairs, so those cookies remain host-only and are never widened to a parent domain.
     */
    fun importWebViewCookies(sourceId: String, spec: AuthSpec.WebCookie) {
        val jar = session(sourceId).jar
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        val originHeaders = buildList {
            spec.validCookieOrigins().forEach { url ->
                val raw = cookieManager.getCookie(url.toString()) ?: return@forEach
                add(url to raw)
            }
        }
        saveWebViewCookieBatch(originHeaders) { cookies ->
            jar.saveAllFromResponses(cookies)
        }
    }

    /** Clears only one source's OkHttp and WebView cookies. */
    fun logout(sourceId: String, spec: AuthSpec.WebCookie) {
        val jar = session(sourceId).jar
        val cookieManager = CookieManager.getInstance()
        spec.validCookieOrigins().forEach { url ->
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
        invalidateSourceCapabilities(sourceId)
        session(sourceId).setState(AuthState.SignedOut)
    }

    private fun session(sourceId: String): SourceSession = synchronized(sessions) {
        val storageKey = sourceIdentities[sourceId] ?: sourceId
        sessions.getOrPut(storageKey) { SourceSession(sourceId, storageKey) }
    }

    private inner class SourceSession(private val sourceId: String, storageKey: String) {
        val jar = SourceCookieJar(context, storageKey)
        private val mutableState = MutableStateFlow(if (jar.isEmpty()) AuthState.SignedOut else AuthState.Unknown)

        init {
            mutableStates.value = mutableStates.value + (sourceId to mutableState.value)
        }

        fun setState(state: AuthState) {
            mutableState.value = state
            mutableStates.value = mutableStates.value + (sourceId to state)
        }
    }

    private data class ResourceBinding(
        val storageKey: String,
        val generation: Long,
        val url: String,
    )

    private data class ExternalLinkBinding(
        val storageKey: String,
        val generation: Long,
        val url: String,
    )

    private fun resolveResource(handle: ResourceHandle): Pair<ResourceBinding, SourceNetworkBroker> =
        synchronized(resources) {
            val binding = requireNotNull(resources[handle.token]) { "Unknown resource handle" }
            require(binding.storageKey.take(16) == handle.sourceSession) { "Resource source mismatch" }
            require(binding.generation == handle.generation) { "Stale resource handle" }
            require(resourceGenerations[binding.storageKey] == handle.generation) { "Revoked resource handle" }
            binding to requireNotNull(resourceBrokers[binding.storageKey]) { "Source broker is unavailable" }
        }

    private fun ensureResourceCurrent(handle: ResourceHandle, binding: ResourceBinding) = synchronized(resources) {
        require(resourceGenerations[binding.storageKey] == handle.generation) { "Resource was revoked during read" }
        require(resources[handle.token] == binding) { "Resource was revoked during read" }
    }

    private fun rotateHandles(storageKey: String) {
        resourceGenerations[storageKey] = (resourceGenerations[storageKey] ?: 0L) + 1L
        resources.entries.removeAll { it.value.storageKey == storageKey }
        externalLinks.entries.removeAll { it.value.storageKey == storageKey }
    }

    private fun invalidateSourceCapabilities(sourceId: String) {
        val storageKey = synchronized(sessions) { sourceIdentities[sourceId] } ?: return
        synchronized(resources) {
            resourceBrokers[storageKey]?.invalidateInFlight()
            rotateHandles(storageKey)
        }
    }

    private fun secureCapabilityToken(): String {
        val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(tokenBytes, Base64.NO_WRAP or Base64.URL_SAFE).trimEnd('=')
    }
}

internal fun validateExternalLink(url: String, policy: SourceNetworkPolicy): HttpUrl {
    require(NamedHostCapabilities.EXTERNAL_LINK in policy.namedCapabilities) {
        "External link capability is not authorized"
    }
    val parsed = url.toHttpsUrlOrNull() ?: throw IllegalArgumentException("External link must be HTTPS")
    require(parsed.port == 443) { "External link uses a non-default port" }
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "External link userinfo is forbidden" }
    require(parsed.host.lowercase(Locale.ROOT) in policy.exactHosts) { "External link host is not authorized" }
    require(!parsed.host.matches(Regex("[0-9.]+")) && ':' !in parsed.host) { "External link IP is forbidden" }
    return parsed
}

private fun SourceIdentity.storageKey(): String {
    val canonical = listOf(packageName, signerSha256.lowercase(Locale.ROOT), sourceId)
        .joinToString(separator = "\u0000", prefix = "newshub-source\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun String.toHttpsUrlOrNull(): HttpUrl? = runCatching {
    toHttpUrl().takeIf { it.isHttps }
}.getOrNull()

private fun AuthSpec.WebCookie.validCookieOrigins(): Set<HttpUrl> {
    val normalizedAllowedHosts = allowedHosts.asSequence()
        .map { it.lowercase(Locale.ROOT) }
        .toHashSet()

    return cookieOrigins.mapNotNullTo(linkedSetOf()) { origin ->
        val url = origin.toHttpsUrlOrNull() ?: return@mapNotNullTo null
        url.takeIf { it.host.lowercase(Locale.ROOT) in normalizedAllowedHosts }
    }
}

/**
 * Parses cookies from every approved WebView origin before saving one persistence batch.
 * The URL supplied to [Cookie.parse] makes cookies without a Domain attribute host-only.
 */
internal fun saveWebViewCookieBatch(
    originHeaders: Iterable<Pair<HttpUrl, String>>,
    saveBatch: (List<Cookie>) -> Unit,
) {
    val cookies = originHeaders.flatMap { (url, raw) ->
        raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
    if (cookies.isNotEmpty()) saveBatch(cookies)
}

/** Applies an RFC cookie-identity merge without persisting; useful for batching and unit tests. */
internal fun mergeCookieBatch(
    existingCookies: List<Cookie>,
    incomingCookies: Iterable<Cookie>,
    now: Long = System.currentTimeMillis(),
): List<Cookie> {
    val mergedCookies = existingCookies.toMutableList()
    incomingCookies.forEach { incoming ->
        // RFC cookie identity includes path. Do not collapse same-name cookies from paths.
        mergedCookies.removeAll {
            it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
        }
        if (incoming.expiresAt >= now) mergedCookies += incoming
    }
    return mergedCookies
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

    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) = saveAllFromResponses(newCookies)

    /** Merges cookies collected from several WebView origins and persists exactly once. */
    fun saveAllFromResponses(newCookies: Iterable<Cookie>) = synchronized(cookies) {
        val mergedCookies = mergeCookieBatch(cookies, newCookies)
        cookies.clear()
        cookies += mergedCookies
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
