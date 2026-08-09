package tw.kevinzhang.newshub.auth

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import tw.kevinzhang.extension_api.SourceNetworkPolicy
import tw.kevinzhang.extension_api.SourceNetworkRequest
import tw.kevinzhang.extension_api.SourceNetworkResponse
import tw.kevinzhang.extension_api.ResourcePayload
import tw.kevinzhang.extension_api.ResourceRange
import tw.kevinzhang.extension_api.EynyChallengeProof
import tw.kevinzhang.extension_api.NamedHostCapabilities
import tw.kevinzhang.extension_api.NetworkOperations
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

/** Source-scoped Binder capability. The immutable identity and policy never come from IPC. */
internal class SourceNetworkBroker(
    private val baseClient: OkHttpClient,
    private val cookieJar: CookieJar,
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
            respond(requestId, ExtensionProtocol.STATUS_FAILED, "Source capability was revoked", callback)
            return
        }
        if (pendingRequests.incrementAndGet() > MAX_PENDING_BROKER_REQUESTS) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respond(requestId, ExtensionProtocol.STATUS_FAILED, "Source request queue is full", callback)
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
                        val validated = validateSourceNetworkRequest(networkRequest, policy)
                        val response = perform(requestId, networkRequest, validated)
                        respond(
                            requestId,
                            ExtensionProtocol.STATUS_OK,
                            ExtensionWireJson.encode(response),
                            callback,
                        )
                    }
                }
            } catch (error: Exception) {
                respond(requestId, ExtensionProtocol.STATUS_FAILED, error.message.orEmpty(), callback)
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
            respond(requestId, ExtensionProtocol.STATUS_FAILED, "Source capability was revoked", callback)
            return
        }
        if (pendingRequests.incrementAndGet() > MAX_PENDING_BROKER_REQUESTS) {
            pendingRequests.decrementAndGet()
            request.closeQuietly()
            respond(requestId, ExtensionProtocol.STATUS_FAILED, "Source request queue is full", callback)
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
            } catch (error: Exception) {
                respond(requestId, ExtensionProtocol.STATUS_FAILED, error.message.orEmpty(), callback)
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
                val validated = validateSourceNetworkRequest(request, policy)
                val response = perform(resourceRequestIds.incrementAndGet(), request, validated)
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
                val validated = validateSourceNetworkRequest(request, policy)
                val response = perform(
                    requestId = resourceRequestIds.incrementAndGet(),
                    networkRequest = request,
                    url = validated,
                    hostHeaders = mapOf("Range" to "bytes=$offset-${offset + length - 1L}"),
                    responseLimit = length,
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
        hostHeaders: Map<String, String> = emptyMap(),
        responseLimit: Int = ExtensionProtocol.MAX_NETWORK_RESPONSE_BYTES,
    ): SourceNetworkResponse {
        val operation = requireNotNull(policy.operations[networkRequest.operation])
        val client = baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .dns(GlobalOnlyDns)
            .cookieJar(if (operation.credentialed) cookieJar else CookieJar.NO_COOKIES)
            .cache(null)
            .build()
        val request = Request.Builder().url(url).method(networkRequest.method, null).apply {
            networkRequest.headers.forEach { (name, value) -> addHeader(name, value) }
            hostHeaders.forEach { (name, value) -> header(name, value) }
        }.build()
        val call = client.newCall(request)
        calls[requestId] = call
        return try {
            call.execute().use { response ->
                val responseHeaders = response.headers.names()
                    .filterNot { it.equals("set-cookie", ignoreCase = true) }
                    .take(48)
                    .associateWith { response.header(it).orEmpty().take(4_096) }
                SourceNetworkResponse(
                    code = response.code,
                    headers = responseHeaders,
                    body = response.body.readBounded(responseLimit),
                )
            }
        } finally {
            calls.remove(requestId)
        }
    }
}

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
): HttpUrl {
    require(request.operation.length in 1..64) { "Invalid operation" }
    val operation = requireNotNull(policy.operations[request.operation]) { "Operation is not authorized" }
    val method = request.method.uppercase(Locale.ROOT)
    require(method == request.method && method in operation.methods) { "HTTP method is not authorized" }
    require(request.body == null || method !in setOf("GET", "HEAD")) { "GET/HEAD body is forbidden" }
    require(request.headers.size <= 24) { "Too many headers" }
    request.headers.forEach { (name, value) ->
        require(name.length in 1..64 && value.length <= 4_096) { "Invalid header" }
        require(name.lowercase(Locale.ROOT) !in FORBIDDEN_EXTENSION_HEADERS) { "Forbidden header" }
        require('\r' !in value && '\n' !in value) { "Invalid header value" }
    }

    val url = request.url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Malformed URL")
    require(url.isHttps) { "Only HTTPS is allowed" }
    require(url.port == 443) { "Non-default ports are forbidden" }
    require(url.username.isEmpty() && url.password.isEmpty()) { "URL userinfo is forbidden" }
    require(url.host.lowercase(Locale.ROOT) in policy.exactHosts) { "Host is not authorized" }
    require(!url.host.isIpLiteral()) { "IP literals are forbidden" }
    require(operation.pathPrefixes.any { prefix ->
        prefix.startsWith('/') && url.encodedPath.startsWith(prefix)
    }) { "Path is not authorized" }
    return url
}

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

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
