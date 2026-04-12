package tw.kevinzhang.newshub.auth

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppCookieJar"
private const val PREFS_NAME = "app_cookies"
private const val PREFS_KEY = "cookie_list"

/**
 * Persistent cookie jar shared between OkHttpClient and the WebView auth flow.
 * Cookies are stored in SharedPreferences so they survive app restarts.
 * Uses a flat list so domain cookies (hostOnly=false) are matched across subdomains.
 */
@Singleton
class AppCookieJar @Inject constructor(
    @ApplicationContext context: Context,
) : CookieJar {

    private data class SerializableCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val expiresAt: Long,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cookies = mutableListOf<Cookie>()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(PREFS_KEY, null) ?: return
        val type = object : TypeToken<List<SerializableCookie>>() {}.type
        val saved: List<SerializableCookie> = gson.fromJson(json, type) ?: return
        val now = System.currentTimeMillis()
        saved
            .filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }
            .mapNotNull { it.toCookie() }
            .let { cookies.addAll(it) }
    }

    private fun persistToPrefs() {
        val serializable = cookies.map { it.toSerializable() }
        prefs.edit().putString(PREFS_KEY, gson.toJson(serializable)).apply()
    }

    private fun SerializableCookie.toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .apply {
                if (secure) secure()
                if (httpOnly) httpOnly()
                if (expiresAt != Long.MAX_VALUE) expiresAt(expiresAt)
            }
            .build()
    }.getOrNull()

    private fun Cookie.toSerializable() = SerializableCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        expiresAt = expiresAt,
    )

    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
        synchronized(cookies) {
            newCookies.forEach { incoming ->
                cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain }
                cookies.add(incoming)
            }
            persistToPrefs()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return synchronized(cookies) { cookies.filter { it.matches(url) } }
    }

    /**
     * Called after WebView login to inject WebView cookies into OkHttp.
     * Cookies are stored as domain cookies (hostOnly=false) scoped to the parent domain
     * so they are sent to all subdomains (e.g., forum.gamer.com.tw, user.gamer.com.tw).
     */
    fun addCookies(url: HttpUrl, rawCookies: List<Cookie>) {
        val parentDomain = parentDomain(url.host)
        Log.d(TAG, "addCookies domain=$parentDomain count=${rawCookies.size}")
        val domainCookies = rawCookies.map { parsed ->
            Cookie.Builder()
                .name(parsed.name)
                .value(parsed.value)
                .domain(parentDomain)   // hostOnly=false, matches all subdomains
                .path(parsed.path)
                .apply {
                    if (parsed.secure) secure()
                    if (parsed.httpOnly) httpOnly()
                }
                .build()
        }
        saveFromResponse(url, domainCookies)
    }

    /** Remove all cookies that match the host of the given full URL (for logout). */
    fun clearCookiesForUrl(url: String) {
        val host = runCatching { url.toHttpUrl().host }.getOrElse { url }
        clearCookiesForDomain(host)
    }

    /** Remove all cookies that match the given domain (for logout). */
    fun clearCookiesForDomain(domain: String) {
        val parentDomain = parentDomain(domain)
        synchronized(cookies) {
            val before = cookies.size
            cookies.removeAll { it.domain == parentDomain || it.domain.endsWith(".$parentDomain") }
            Log.d(TAG, "clearCookiesForDomain domain=$parentDomain removed=${before - cookies.size}")
            persistToPrefs()
        }
    }

    fun hasCookiesForUrl(url: String): Boolean {
        val host = runCatching { url.toHttpUrl().host }.getOrElse { url }
        return hasCookiesForDomain(host)
    }

    fun hasCookiesForDomain(domain: String): Boolean {
        val parentDomain = parentDomain(domain)
        return synchronized(cookies) {
            cookies.any { it.domain == parentDomain || it.domain.endsWith(".$parentDomain") }
        }
    }

    /**
     * Parses a raw cookie string returned by [android.webkit.CookieManager.getCookie] and
     * adds the cookies to the jar. Called after the extension's LoginActivity returns its result.
     */
    fun addCookiesFromString(url: String, rawCookies: String) {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return
        val cookies = rawCookies.split(";")
            .mapNotNull { Cookie.parse(httpUrl, it.trim()) }
        if (cookies.isNotEmpty()) addCookies(httpUrl, cookies)
    }

}

/** Strips the leading subdomain to get the eTLD+1 (e.g., www.gamer.com.tw → gamer.com.tw). */
internal fun parentDomain(host: String): String {
    val parts = host.split(".")
    return if (parts.size > 2) parts.drop(1).joinToString(".") else host
}
