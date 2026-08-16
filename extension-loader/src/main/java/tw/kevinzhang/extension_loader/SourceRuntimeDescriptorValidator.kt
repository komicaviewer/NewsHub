package tw.kevinzhang.extension_loader

import java.net.URI
import java.util.Locale
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceRuntimeDescriptor
import tw.kevinzhang.extension_api.WebCookieAuthDescriptor

internal data class ValidatedSourceRuntimeDescriptor(
    val sourceVersion: Int,
    val iconUrl: String?,
    val supportsCommentPagination: Boolean,
    val alwaysUseRawImage: Boolean,
    val needsLogin: Boolean,
    val authSpec: AuthSpec.WebCookie?,
    val webLoginUserAgent: String?,
)

/** Validates every extension-controlled descriptor field before it reaches app code. */
internal fun validateSourceRuntimeDescriptor(
    runtime: SourceRuntimeDescriptor,
    manifest: ExtensionDescriptor,
    policy: SourceNetworkPolicy,
): ValidatedSourceRuntimeDescriptor {
    require(runtime.protocolVersion == ExtensionProtocol.VERSION) { "Runtime protocol mismatch" }
    require(runtime.sourceId == manifest.sourceId) { "Runtime Source id mismatch" }
    require(runtime.name == manifest.name) { "Runtime Source name mismatch" }
    require(runtime.language == manifest.lang) { "Runtime Source language mismatch" }
    require(runtime.sourceVersion > 0) { "Runtime Source version must be positive" }
    require(runtime.name.isNotBlank() && runtime.name.length <= MAX_RUNTIME_TEXT_BYTES) {
        "Runtime Source name is invalid"
    }
    require(runtime.language.matches(Regex("[A-Za-z0-9-]{1,35}"))) { "Runtime Source language is invalid" }

    val iconUrl = runtime.iconUrl?.also { url ->
        require(url.length <= MAX_RUNTIME_URL_BYTES) { "Runtime icon URL is too long" }
        val parsed = canonicalHttpsUrl(url, allowQuery = true)
        require(parsed.host.lowercase(Locale.ROOT) in policy.resourceExactHosts) {
            "Runtime icon host is outside the signed resource policy"
        }
    }

    val authSpec = runtime.webCookieAuth?.validated(policy)
    require(!runtime.needsLogin || authSpec != null) {
        "Runtime Source requiring login must declare WebCookie authentication"
    }
    val userAgent = runtime.webLoginUserAgent?.also { value ->
        require(authSpec != null) { "Runtime Web login User-Agent requires WebCookie authentication" }
        require(value.isNotBlank() && value.length <= MAX_USER_AGENT_BYTES && value == value.trim()) {
            "Runtime Web login User-Agent is invalid"
        }
        require(value.none { it.code < 0x20 || it.code == 0x7f }) {
            "Runtime Web login User-Agent contains control characters"
        }
    }

    return ValidatedSourceRuntimeDescriptor(
        sourceVersion = runtime.sourceVersion,
        iconUrl = iconUrl,
        supportsCommentPagination = runtime.supportsCommentPagination,
        alwaysUseRawImage = runtime.alwaysUseRawImage,
        needsLogin = runtime.needsLogin,
        authSpec = authSpec,
        webLoginUserAgent = userAgent,
    )
}

private fun WebCookieAuthDescriptor.validated(policy: SourceNetworkPolicy): AuthSpec.WebCookie {
    require(allowedHosts.isNotEmpty() && allowedHosts.size <= MAX_AUTH_ITEMS) {
        "Runtime authentication hosts must be non-empty and bounded"
    }
    require(cookieOrigins.isNotEmpty() && cookieOrigins.size <= MAX_AUTH_ITEMS) {
        "Runtime cookie origins must be non-empty and bounded"
    }
    require(cookieDomains.size <= MAX_AUTH_ITEMS) { "Runtime cookie domains are too numerous" }

    val hosts = allowedHosts.onEach { host ->
        require(host.isCanonicalExactDnsHost()) { "Runtime authentication host is not canonical" }
        require(host in policy.authExactHosts) { "Runtime authentication host is outside signed auth policy" }
    }
    val parsedLoginUrl = canonicalHttpsUrl(loginUrl, allowQuery = true)
    require(loginUrl.length <= MAX_RUNTIME_URL_BYTES) { "Runtime login URL is too long" }
    require(parsedLoginUrl.host.lowercase(Locale.ROOT) in hosts) {
        "Runtime login URL host is not an allowed authentication host"
    }

    cookieOrigins.forEach { origin ->
        require(origin.length <= MAX_RUNTIME_URL_BYTES) { "Runtime cookie origin is too long" }
        val parsed = canonicalHttpsUrl(origin, allowQuery = false)
        require(parsed.rawPath.isNullOrEmpty() || parsed.rawPath == "/") {
            "Runtime cookie origin must not contain a path"
        }
        require(parsed.host.lowercase(Locale.ROOT) in hosts) {
            "Runtime cookie origin host is not an allowed authentication host"
        }
    }
    cookieDomains.forEach { domain ->
        require(domain.isCanonicalExactDnsHost()) { "Runtime cookie domain is not canonical" }
        require(hosts.any { host -> host == domain || host.endsWith(".$domain") }) {
            "Runtime cookie domain does not contain an allowed authentication host"
        }
    }

    return AuthSpec.WebCookie(
        loginUrl = loginUrl,
        allowedHosts = hosts.toSet(),
        cookieOrigins = cookieOrigins.toSet(),
        cookieDomains = cookieDomains.toSet(),
        javaScriptEnabled = javaScriptEnabled,
    )
}

private fun canonicalHttpsUrl(value: String, allowQuery: Boolean): URI {
    val parsed = runCatching { URI(value) }.getOrNull()
    require(parsed != null && parsed.isAbsolute && parsed.scheme == "https") { "Runtime URL must be HTTPS" }
    require(parsed.rawUserInfo == null && parsed.rawFragment == null) { "Runtime URL contains forbidden components" }
    require(allowQuery || parsed.rawQuery == null) { "Runtime origin must not contain a query" }
    require(parsed.port == -1 || parsed.port == 443) { "Runtime URL uses a non-default port" }
    val host = parsed.host?.lowercase(Locale.ROOT)
    require(host != null && host == parsed.host && host.isCanonicalExactDnsHost()) {
        "Runtime URL host is not canonical"
    }
    return parsed
}

private fun String.isCanonicalExactDnsHost(): Boolean {
    if (length !in 1..253 || this != lowercase(Locale.ROOT) || '*' in this || ':' in this ||
        startsWith('.') || endsWith('.') || ".." in this || isIpv4Literal()
    ) return false
    val labels = split('.')
    return labels.size >= 2 && labels.all { label ->
        label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

private fun String.isIpv4Literal(): Boolean = split('.').let { labels ->
    labels.size == 4 && labels.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
}

private const val MAX_RUNTIME_TEXT_BYTES = 512
private const val MAX_RUNTIME_URL_BYTES = 2048
private const val MAX_USER_AGENT_BYTES = 512
private const val MAX_AUTH_ITEMS = 32
