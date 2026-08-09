package tw.kevinzhang.newshub.auth

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    private val concurrency = Semaphore(2)

    override fun execute(
        requestId: Long,
        request: ParcelFileDescriptor,
        callback: IHostBrokerCallback,
    ) {
        if (jobs.containsKey(requestId)) {
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
            }
        }
    }

    override fun cancel(requestId: Long) {
        jobs.remove(requestId)?.cancel()
        calls.remove(requestId)?.cancel()
    }

    suspend fun fetchResource(url: String): ResourcePayload = withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val request = SourceNetworkRequest("resource", "GET", url)
            val validated = validateSourceNetworkRequest(request, policy)
            val response = perform(Long.MIN_VALUE, request, validated)
            require(response.code in 200..299) { "Resource fetch failed: HTTP ${response.code}" }
            ResourcePayload(response.body, response.headers.entries
                .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                ?.value)
        }
    }

    private fun perform(
        requestId: Long,
        networkRequest: SourceNetworkRequest,
        url: HttpUrl,
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
        }.build()
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
                body = response.body.readBounded(ExtensionProtocol.MAX_NETWORK_RESPONSE_BYTES),
            )
        }
    }
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
