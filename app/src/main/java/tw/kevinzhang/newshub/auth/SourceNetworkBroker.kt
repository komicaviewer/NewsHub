package tw.kevinzhang.newshub.auth

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.ExtensionWireJson
import tw.kevinzhang.extension_api.IHostBroker
import tw.kevinzhang.extension_api.IHostBrokerCallback
import tw.kevinzhang.extension_api.PipePayload
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.SourceFailure
import tw.kevinzhang.extension_api.SourceFailureCode
import tw.kevinzhang.extension_api.SourceFailureException
import tw.kevinzhang.extension_api.SourceFailures
import tw.kevinzhang.extension_api.SourceFailureWire
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceNetworkResponse
import tw.kevinzhang.extension_api.ResourcePayload
import tw.kevinzhang.extension_api.ResourceRange
import tw.kevinzhang.extension_api.EynyChallengeProof
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperations
import tw.kevinzhang.extension_api.NetworkRequestRule
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import tw.kevinzhang.newshub.auth.oauth.OAuthCredentialProvider
import tw.kevinzhang.newshub.auth.oauth.OAuth1CredentialProvider

private val FORBIDDEN_EXTENSION_HEADERS = setOf(
    "authorization",
    "cookie",
    "set-cookie",
    "host",
    "proxy-authorization",
    "proxy-connection",
    "connection",
    "transfer-encoding",
    "content-length",
    "te",
    "trailer",
    "upgrade",
)

private const val PTT_PACKAGE = "tw.kevinzhang.newshub.extension.ptt"
private const val PTT_SOURCE = "tw.kevinzhang.newshub.extension.ptt"
private const val EYNY_PACKAGE = "tw.kevinzhang.newshub.extension.eyny"
private const val EYNY_SOURCE = "tw.kevinzhang.eyny"
private const val EYNY_COOKIE_DOMAIN = "eyny.com"
private const val EYNY_PROOF_TTL_MILLIS = 24L * 60L * 60L * 1_000L
internal const val MAX_RESOURCE_RANGE_BYTES = 512 * 1_024
private const val MAX_PENDING_BROKER_REQUESTS = 8
internal const val MAX_SOURCE_REDIRECTS = 5

/** Source-scoped Binder capability. The immutable identity and policy never come from IPC. */
internal class SourceNetworkBroker(
    private val baseClient: OkHttpClient,
    private val cookieJar: CookieJar,
    private val oauthCredentialProvider: OAuthCredentialProvider,
    private val oauth1CredentialProvider: OAuth1CredentialProvider,
    private val identity: SourceIdentity,
    private val policy: SourceNetworkPolicy,
) : IHostBroker.Stub() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val calls = ConcurrentHashMap<Long, Call>()
    private val resourceRequestIds = AtomicLong(Long.MIN_VALUE)
    private val concurrency = Semaphore(2)
    private val active = AtomicBoolean(true)
    private val pendingRequests = AtomicInteger()

    override fun execute(
        requestId: Long,
        request: ParcelFileDescriptor,
        callback: IHostBrokerCallback,
    ) {
        if (!active.get()) {
            request.closeQuietly()
            respondFailure(requestId, SourceFailure(SourceFailureCode.TRUST_INACTIVE), callback)
            return
        }
        if (pendingRequests.incrementAndGet() > MAX_PENDING_BROKER_REQUESTS) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respondFailure(requestId, SourceFailure(SourceFailureCode.BACKPRESSURE, retryable = true), callback)
            return
        }
        if (jobs.containsKey(requestId)) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respond(requestId, ExtensionProtocol.STATUS_INVALID_REQUEST, "duplicate request", callback)
            return
        }
        jobs[requestId] = scope.launch {
            try {
                concurrency.withPermit {
                    withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                        val payload = PipePayload.readUtf8(request, ExtensionProtocol.MAX_NETWORK_REQUEST_BYTES)
                        val networkRequest = ExtensionWireJson.decode<SourceNetworkRequest>(payload)
                        val authorized = authorizeSourceNetworkRequest(networkRequest, policy)
                        val response = followAuthorizedSourceRedirects(
                            request = networkRequest,
                            initial = authorized,
                            policy = policy,
                        ) { redirectUrl, credentialed ->
                            perform(
                                requestId = requestId,
                                networkRequest = networkRequest.copy(url = redirectUrl.toString()),
                                url = redirectUrl,
                                credentialed = credentialed,
                            )
                        }
                        respond(
                            requestId,
                            ExtensionProtocol.STATUS_OK,
                            ExtensionWireJson.encode(response),
                            callback,
                        )
                    }
                }
            } catch (_: TimeoutCancellationException) {
                respondFailure(requestId, SourceFailure(SourceFailureCode.TIMED_OUT), callback)
            } catch (_: CancellationException) {
                respond(requestId, ExtensionProtocol.STATUS_CANCELLED, "cancelled", callback)
            } catch (error: Exception) {
                respondFailure(requestId, SourceFailures.fromThrowable(error), callback)
            } finally {
                request.closeQuietly()
                calls.remove(requestId)?.cancel()
                jobs.remove(requestId)
                pendingRequests.decrementAndGet()
            }
        }
    }

    override fun cancel(requestId: Long) {
        jobs.remove(requestId)?.cancel()
        calls.remove(requestId)?.cancel()
    }

    override fun executeNamedCookieOperation(
        requestId: Long,
        operation: Int,
        request: ParcelFileDescriptor,
        callback: IHostBrokerCallback,
    ) {
        if (!active.get()) {
            request.closeQuietly()
            respondFailure(requestId, SourceFailure(SourceFailureCode.TRUST_INACTIVE), callback)
            return
        }
        if (pendingRequests.incrementAndGet() > MAX_PENDING_BROKER_REQUESTS) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respondFailure(requestId, SourceFailure(SourceFailureCode.BACKPRESSURE, retryable = true), callback)
            return
        }
        if (jobs.containsKey(requestId)) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respond(requestId, ExtensionProtocol.STATUS_INVALID_REQUEST, "duplicate request", callback)
            return
        }
        jobs[requestId] = scope.launch {
            try {
                concurrency.withPermit {
                    withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                        val payload = PipePayload.readUtf8(request, ExtensionProtocol.MAX_CONTROL_BYTES)
                        val result = executeNamedCookieOperation(
                            identity = identity,
                            policy = policy,
                            cookieJar = cookieJar,
                            operation = operation,
                            payload = payload,
                        )
                        respond(requestId, ExtensionProtocol.STATUS_OK, result, callback)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                respondFailure(requestId, SourceFailure(SourceFailureCode.TIMED_OUT), callback)
            } catch (_: CancellationException) {
                respond(requestId, ExtensionProtocol.STATUS_CANCELLED, "cancelled", callback)
            } catch (error: Exception) {
                respondFailure(requestId, SourceFailures.fromThrowable(error), callback)
            } finally {
                request.closeQuietly()
                jobs.remove(requestId)
                pendingRequests.decrementAndGet()
            }
        }
    }

    fun revoke() {
        if (!active.compareAndSet(true, false)) return
        invalidateInFlight()
        scope.cancel()
    }

    /** Cancels requests crossing an authentication/generation boundary without disabling public reads. */
    fun invalidateInFlight() {
        jobs.values.forEach(Job::cancel)
        calls.values.forEach(Call::cancel)
        jobs.clear()
        calls.clear()
    }

    suspend fun fetchResource(url: String): ResourcePayload = withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
        check(active.get()) { "Source capability was revoked" }
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            concurrency.withPermit {
                val request = SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", url)
                val validated = validateResourceUrl(url, policy)
                val response = followBoundedSameOriginRedirects(
                    initialUrl = validated,
                    operation = request.operation,
                    validateRedirect = { redirectUrl ->
                        validateResourceUrl(redirectUrl.toString(), policy)
                    },
                    execute = { redirectUrl ->
                        perform(
                            requestId = resourceRequestIds.incrementAndGet(),
                            networkRequest = request.copy(url = redirectUrl.toString()),
                            url = redirectUrl,
                            credentialed = false,
                        )
                    },
                )
                require(response.code in 200..299) { "Resource fetch failed: HTTP ${response.code}" }
                ResourcePayload(response.body, response.headers.entries
                    .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                    ?.value)
            }
        }
    }

    suspend fun fetchResourceRange(url: String, offset: Long, length: Int): ResourceRange =
        withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
            check(active.get()) { "Source capability was revoked" }
            require(offset >= 0L) { "Invalid range offset" }
            require(length in 1..MAX_RESOURCE_RANGE_BYTES) { "Invalid range length" }
            require(offset <= Long.MAX_VALUE - length.toLong()) { "Range offset overflow" }
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                concurrency.withPermit {
                val request = SourceNetworkRequest(NetworkOperations.SOURCE_READ, "GET", url)
                val validated = validateResourceUrl(url, policy)
                val response = followBoundedSameOriginRedirects(
                    initialUrl = validated,
                    operation = request.operation,
                    validateRedirect = { redirectUrl ->
                        validateResourceUrl(redirectUrl.toString(), policy)
                    },
                    execute = { redirectUrl ->
                        perform(
                            requestId = resourceRequestIds.incrementAndGet(),
                            networkRequest = request.copy(url = redirectUrl.toString()),
                            url = redirectUrl,
                            credentialed = false,
                            hostHeaders = mapOf("Range" to "bytes=$offset-${offset + length - 1L}"),
                            responseLimit = length,
                        )
                    },
                )
                require(response.code == 200 || response.code == 206) {
                    "Resource range failed: HTTP ${response.code}"
                }
                val contentRange = response.headers.entries
                    .firstOrNull { it.key.equals("content-range", ignoreCase = true) }
                    ?.value
                val parsedRange = parseContentRange(contentRange)
                if (response.code == 206) {
                    val range = requireNotNull(parsedRange) { "Resource omitted Content-Range" }
                    require(range.start == offset) { "Resource returned an unexpected range" }
                    require(range.endInclusive >= range.start) { "Resource returned an invalid range" }
                    require(range.endInclusive - range.start + 1L == response.body.size.toLong()) {
                        "Resource range length mismatch"
                    }
                    require(range.endInclusive < offset + length.toLong()) { "Resource exceeded requested range" }
                    require(range.totalLength == null || range.totalLength > range.endInclusive) {
                        "Resource returned an invalid total length"
                    }
                } else {
                    require(offset == 0L) { "Resource ignored a non-zero range" }
                }
                ResourceRange(
                    bytes = response.body,
                    contentType = response.headers.entries
                        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                        ?.value,
                    offset = if (response.code == 206) requireNotNull(parsedRange).start else 0L,
                    totalLength = parsedRange?.totalLength
                        ?: response.headers.entries
                            .firstOrNull { it.key.equals("content-length", ignoreCase = true) }
                            ?.value
                            ?.toLongOrNull(),
                )
                }
            }
        }

    private fun perform(
        requestId: Long,
        networkRequest: SourceNetworkRequest,
        url: HttpUrl,
        credentialed: Boolean,
        hostHeaders: Map<String, String> = emptyMap(),
        responseLimit: Int = ExtensionProtocol.MAX_NETWORK_RESPONSE_BYTES,
    ): SourceNetworkResponse {
        val client = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .dns(GlobalOnlyDns)
            .cookieJar(if (credentialed) cookieJar else CookieJar.NO_COOKIES)
            .cache(null)
            .build()
        fun buildRequest(forceRefresh: Boolean): Request =
            Request.Builder().url(url).method(networkRequest.method, null).apply {
                networkRequest.headers.forEach { (name, value) -> addHeader(name, value) }
                hostHeaders.forEach { (name, value) -> header(name, value) }
                if (credentialed) {
                    val bearer = oauthCredentialProvider.authorizationHeader(
                        identity,
                        url,
                        forceRefresh = forceRefresh,
                    )
                    if (bearer != null) {
                        header("Authorization", bearer)
                    } else {
                        oauth1CredentialProvider.authorizationHeader(identity, networkRequest.method, url)
                            ?.let { oauth1 -> header("Authorization", oauth1) }
                    }
                }
            }.build()

        fun executeOnce(request: Request): SourceNetworkResponse {
            val call = client.newCall(request)
            calls[requestId] = call
            return call.execute().use { response ->
                val responseHeaders = response.headers.names()
                    .filterNot { it.equals("set-cookie", ignoreCase = true) }
                    .take(48)
                    .associateWith { response.header(it).orEmpty().take(4_096) }
                SourceNetworkResponse(
                    code = response.code,
                    headers = responseHeaders,
                    body = if (response.code in REDIRECT_STATUS_CODES) {
                        ByteArray(0)
                    } else {
                        response.body.readBounded(responseLimit)
                    },
                )
            }
        }

        return try {
            val initialRequest = buildRequest(forceRefresh = false)
            val initialResponse = executeOnce(initialRequest)
            if (shouldRetryBearer401(initialRequest, initialResponse)) {
                val refreshedRequest = buildRequest(forceRefresh = true)
                if (refreshedRequest.header("Authorization") != null) {
                    executeOnce(refreshedRequest)
                } else {
                    initialResponse
                }
            } else {
                initialResponse
            }
        } finally {
            calls.remove(requestId)
        }
    }
}

internal fun shouldRetryBearer401(request: Request, response: SourceNetworkResponse): Boolean =
    response.code == 401 &&
        request.header("Authorization")?.startsWith("Bearer ", ignoreCase = true) == true

internal fun executeNamedCookieOperation(
    identity: SourceIdentity,
    policy: SourceNetworkPolicy,
    cookieJar: CookieJar,
    operation: Int,
    payload: String,
    now: Long = System.currentTimeMillis(),
): String = when (operation) {
    ExtensionProtocol.COOKIE_OP_PTT_ADULT_CONSENT_STATUS -> {
        require(NamedHostCapabilities.PTT_ADULT_CONSENT_STATUS in policy.namedCapabilities) {
            "PTT consent capability is not authorized"
        }
        require(identity.packageName == PTT_PACKAGE && identity.sourceId == PTT_SOURCE) {
            "PTT consent capability belongs to a different Source"
        }
        require("www.ptt.cc" in policy.authExactHosts) {
            "PTT consent host is not authorized for authentication"
        }
        val consentUrl = requireNotNull("https://www.ptt.cc/".toHttpUrlOrNull())
        ExtensionWireJson.encode(
            cookieJar.loadForRequest(consentUrl).any { cookie ->
                cookie.name == "over18" &&
                    cookie.value == "1" &&
                    cookie.domain == "www.ptt.cc" &&
                    cookie.hostOnly &&
                    cookie.path == "/"
            },
        )
    }
    ExtensionProtocol.COOKIE_OP_EYNY_CHALLENGE_PROOF -> {
        require(NamedHostCapabilities.EYNY_CHALLENGE_PROOF in policy.namedCapabilities) {
            "EYNY challenge capability is not authorized"
        }
        require(identity.packageName == EYNY_PACKAGE && identity.sourceId == EYNY_SOURCE) {
            "EYNY challenge capability belongs to a different Source"
        }
        val proof = ExtensionWireJson.decode<EynyChallengeProof>(payload)
        require(proof.host.lowercase(Locale.ROOT) in policy.authExactHosts) {
            "EYNY proof host is not authorized for authentication"
        }
        val origin = requireNotNull("https://${proof.host}/".toHttpUrlOrNull())
        val expiresAt = now + EYNY_PROOF_TTL_MILLIS
        val values = listOf(
            "${proof.cookiePrefix}_n" to proof.nonce.toString(),
            "${proof.cookiePrefix}_ts" to proof.timestamp,
            "${proof.cookiePrefix}_ch" to proof.challenge,
        )
        cookieJar.saveFromResponse(
            origin,
            values.flatMap { (name, value) ->
                val domainCookie = okhttp3.Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(EYNY_COOKIE_DOMAIN)
                        .path("/")
                        .secure()
                        .expiresAt(expiresAt)
                        .build()
                if (proof.host == EYNY_COOKIE_DOMAIN) {
                    // A host-only and Domain cookie have the same RFC identity here; keep the
                    // wider fixed-domain form deliberately instead of relying on merge order.
                    listOf(domainCookie)
                } else {
                    listOf(
                        okhttp3.Cookie.Builder()
                            .name(name)
                            .value(value)
                            .hostOnlyDomain(proof.host)
                            .path("/")
                            .secure()
                            .expiresAt(expiresAt)
                            .build(),
                        domainCookie,
                    )
                }
            },
        )
        ExtensionWireJson.encode(true)
    }
    else -> throw IllegalArgumentException("Unknown named cookie operation")
}

private data class ParsedContentRange(
    val start: Long,
    val endInclusive: Long,
    val totalLength: Long?,
)

private fun parseContentRange(value: String?): ParsedContentRange? {
    val match = value?.let { Regex("^bytes ([0-9]+)-([0-9]+)/([0-9]+|\\*)$").matchEntire(it) }
        ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val endInclusive = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
    return ParsedContentRange(start, endInclusive, total)
}

internal fun validateSourceNetworkRequest(
    request: SourceNetworkRequest,
    policy: SourceNetworkPolicy,
): HttpUrl = authorizeSourceNetworkRequest(request, policy).url

internal data class AuthorizedNetworkRequest(
    val url: HttpUrl,
    val rule: NetworkRequestRule,
)

internal fun authorizeSourceNetworkRequest(
    request: SourceNetworkRequest,
    policy: SourceNetworkPolicy,
): AuthorizedNetworkRequest {
    fun reject(code: SourceFailureCode, observedHost: String? = null): Nothing =
        throw SourceFailureException(
            SourceFailure(
                code = code,
                operation = request.operation,
                observedHost = observedHost,
                allowedHosts = if (code == SourceFailureCode.HOST_POLICY) policy.exactHosts.toList() else emptyList(),
            ).sanitized(),
        )

    if (request.operation.length !in 1..64) reject(SourceFailureCode.INVALID_REQUEST)
    val method = request.method.uppercase(Locale.ROOT)
    if (method != request.method || method !in setOf("GET", "HEAD")) reject(SourceFailureCode.HOST_POLICY)
    if (request.body != null && method in setOf("GET", "HEAD")) reject(SourceFailureCode.INVALID_REQUEST)
    if (request.headers.size > 24) reject(SourceFailureCode.INVALID_REQUEST)
    request.headers.forEach { (name, value) ->
        if (name.length !in 1..64 || value.length > 4_096) reject(SourceFailureCode.INVALID_REQUEST)
        if (name.lowercase(Locale.ROOT) in FORBIDDEN_EXTENSION_HEADERS) reject(SourceFailureCode.HOST_POLICY)
        if ('\r' in value || '\n' in value) reject(SourceFailureCode.INVALID_REQUEST)
    }

    val url = request.url.toHttpUrlOrNull() ?: reject(SourceFailureCode.INVALID_REQUEST)
    val observedHost = url.host.lowercase(Locale.ROOT)
    if (!url.isHttps || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) {
        reject(SourceFailureCode.HOST_POLICY, observedHost)
    }
    if (observedHost !in policy.exactHosts || url.host.isIpLiteral()) {
        reject(SourceFailureCode.HOST_POLICY, observedHost)
    }
    val matchingRules = policy.requestRules.filter { rule ->
        request.operation == rule.operation.name &&
            observedHost in rule.exactHosts &&
            method in rule.operation.methods &&
            rule.operation.pathPrefixes.any { prefix ->
                prefix.startsWith('/') && url.encodedPath.startsWith(prefix)
            }
    }
    if (matchingRules.size != 1) reject(SourceFailureCode.HOST_POLICY, observedHost)
    return AuthorizedNetworkRequest(url, matchingRules.single())
}

/**
 * Follows only bounded, same-origin redirects in the Host process. The extension receives the
 * terminal response but never gains ambient OkHttp redirect authority.
 */
internal fun followAuthorizedSourceRedirects(
    request: SourceNetworkRequest,
    initial: AuthorizedNetworkRequest,
    policy: SourceNetworkPolicy,
    execute: (url: HttpUrl, credentialed: Boolean) -> SourceNetworkResponse,
): SourceNetworkResponse {
    val credentialed = initial.rule.operation.credentialed
    return followBoundedSameOriginRedirects(
        initialUrl = initial.url,
        operation = request.operation,
        validateRedirect = { redirectUrl ->
            val redirected = authorizeSourceNetworkRequest(
                request.copy(url = redirectUrl.toString()),
                policy,
            )
            // Never let a server redirect move a request across a cookie-authority boundary.
            if (redirected.rule.operation.credentialed != credentialed) {
                throwRedirectFailure(
                    code = SourceFailureCode.HOST_POLICY,
                    operation = request.operation,
                    observedHost = redirectUrl.host,
                    allowedHosts = listOf(initial.url.host),
                )
            }
        },
        execute = { redirectUrl -> execute(redirectUrl, credentialed) },
    )
}

internal fun followBoundedSameOriginRedirects(
    initialUrl: HttpUrl,
    operation: String,
    validateRedirect: (HttpUrl) -> Unit,
    execute: (HttpUrl) -> SourceNetworkResponse,
): SourceNetworkResponse {
    var currentUrl = initialUrl
    val visitedUrls = linkedSetOf(initialUrl.toString())

    repeat(MAX_SOURCE_REDIRECTS + 1) { requestIndex ->
        val response = execute(currentUrl)
        if (response.code !in REDIRECT_STATUS_CODES) return response

        val location = response.headers.entries
            .firstOrNull { (name, _) -> name.equals("location", ignoreCase = true) }
            ?.value
        val redirectUrl = location
            ?.let { value -> runCatching { currentUrl.resolve(value) }.getOrNull() }
            ?: throwRedirectFailure(SourceFailureCode.SITE_UNAVAILABLE, operation)

        if (!redirectUrl.isHttps ||
            redirectUrl.port != 443 ||
            redirectUrl.username.isNotEmpty() ||
            redirectUrl.password.isNotEmpty()
        ) {
            throwRedirectFailure(
                code = SourceFailureCode.HOST_POLICY,
                operation = operation,
                observedHost = redirectUrl.host,
                allowedHosts = listOf(initialUrl.host),
            )
        }

        // A cross-origin redirect is never followed with the initial request's authority. Return
        // the empty redirect response to the isolated Source instead. A Source with an explicit,
        // reviewed redirect policy may issue a new request, which the broker authorizes afresh.
        if (!redirectUrl.hasSameOrigin(initialUrl)) return response

        validateRedirect(redirectUrl)
        if (!visitedUrls.add(redirectUrl.toString()) || requestIndex == MAX_SOURCE_REDIRECTS) {
            throwRedirectFailure(SourceFailureCode.SITE_UNAVAILABLE, operation)
        }
        currentUrl = redirectUrl
    }
    throwRedirectFailure(SourceFailureCode.SITE_UNAVAILABLE, operation)
}

private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private fun throwRedirectFailure(
    code: SourceFailureCode,
    operation: String,
    observedHost: String? = null,
    allowedHosts: List<String> = emptyList(),
): Nothing = throw SourceFailureException(
    SourceFailure(
        code = code,
        operation = operation,
        observedHost = observedHost,
        allowedHosts = allowedHosts,
    ).sanitized(),
)

private object GlobalOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        require(!hostname.isIpLiteral()) { "IP literals are forbidden" }
        val addresses = Dns.SYSTEM.lookup(hostname)
        require(addresses.isNotEmpty() && addresses.all(InetAddress::isGloballyRoutable)) {
            "DNS returned a non-global address"
        }
        return addresses
    }
}

private fun String.isIpLiteral(): Boolean =
    matches(Regex("[0-9.]+")) || ':' in this

private fun InetAddress.isGloballyRoutable(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
        return false
    }
    val bytes = address
    return when (this) {
        is Inet4Address -> {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            !(first == 0 || first == 100 && second in 64..127 || first >= 224 || first == 169 && second == 254)
        }
        is Inet6Address -> {
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            !(first and 0xfe == 0xfc || first == 0xfe && second and 0xc0 == 0x80)
        }
        else -> false
    }
}

private fun ResponseBody?.readBounded(maxBytes: Int): ByteArray {
    if (this == null) return ByteArray(0)
    byteStream().use { input ->
        val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Response exceeded byte limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}

private fun respond(
    requestId: Long,
    status: Int,
    message: String,
    callback: IHostBrokerCallback,
) {
    val pipe = PipePayload.writeUtf8(message.take(ExtensionProtocol.MAX_NETWORK_RESPONSE_BYTES))
    runCatching { callback.onResult(requestId, status, pipe) }
    pipe.closeQuietly()
}

private fun respondFailure(
    requestId: Long,
    failure: SourceFailure,
    callback: IHostBrokerCallback,
) {
    respond(
        requestId,
        ExtensionProtocol.STATUS_SOURCE_FAILURE,
        SourceFailureWire.encode(failure),
        callback,
    )
}

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
