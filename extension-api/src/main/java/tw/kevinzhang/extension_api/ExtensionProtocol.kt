package tw.kevinzhang.extension_api

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.Os
import android.system.OsConstants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.ThreadPage
import tw.kevinzhang.extension_api.model.ThreadSummary
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Current, breaking extension protocol. There is deliberately no legacy fallback. */
object ExtensionProtocol {
    const val VERSION = 2
    const val SERVICE_ACTION = "tw.kevinzhang.newshub.extension.SERVICE"
    const val BIND_PERMISSION = "tw.kevinzhang.newshub.permission.BIND_EXTENSION"

    const val META_PROTOCOL = "newshub.extension.protocol"
    const val META_SOURCE_ID = "newshub.extension.source_id"
    const val META_SOURCE_NAME = "newshub.extension.source_name"
    const val META_SOURCE_LANG = "newshub.extension.source_lang"
    const val META_SOURCE_BASE_URL = "newshub.extension.source_base_url"
    const val META_NEEDS_LOGIN = "newshub.extension.needs_login"
    const val META_LOGIN_URL = "newshub.extension.login_url"
    const val META_LOGIN_HOSTS = "newshub.extension.login_hosts"

    const val OP_BOARD_CATEGORIES = 1
    const val OP_BOARD_PAGE = 2
    const val OP_THREAD_SUMMARIES = 3
    const val OP_THREAD = 4
    const val OP_THREAD_PAGE = 5
    const val OP_COMMENTS = 6
    const val OP_WEB_URL = 7
    const val OP_VALIDATE_SESSION = 8
    const val OP_BOARD_WEB_URL = 9
    /** First operation after binding. The Host refuses every Source until this succeeds. */
    const val OP_RUNTIME_DESCRIPTOR = 10

    const val STATUS_OK = 0
    const val STATUS_INVALID_REQUEST = 1
    const val STATUS_FAILED = 2
    const val STATUS_CANCELLED = 3
    const val STATUS_PAYLOAD_TOO_LARGE = 4
    const val STATUS_SOURCE_FAILURE = 5

    const val COOKIE_OP_PTT_ADULT_CONSENT_STATUS = 1
    const val COOKIE_OP_EYNY_CHALLENGE_PROOF = 2

    const val MAX_CONTROL_BYTES = 64 * 1024
    const val MAX_DESCRIPTOR_BYTES = 32 * 1024
    const val MAX_RESULT_BYTES = 4 * 1024 * 1024
    const val MAX_NETWORK_REQUEST_BYTES = 2 * 1024 * 1024
    const val MAX_NETWORK_RESPONSE_BYTES = 8 * 1024 * 1024
    const val REQUEST_TIMEOUT_MS = 20_000L
}

/** Shared JSON codec. Payload size is enforced before parsing; polymorphic types are explicit. */
object ExtensionWireJson {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Paragraph::class.java, ParagraphAdapter())
        .disableHtmlEscaping()
        .create()

    inline fun <reified T> encode(value: T): String = gson.toJson(value)
    inline fun <reified T> decode(value: String): T = gson.fromJson(value, object : TypeToken<T>() {}.type)
}

private class ParagraphAdapter : JsonSerializer<Paragraph>, JsonDeserializer<Paragraph> {
    override fun serialize(
        source: Paragraph,
        type: java.lang.reflect.Type,
        context: JsonSerializationContext,
    ): JsonElement = JsonObject().apply {
        val (kind, concrete) = when (source) {
            is Paragraph.ImageInfo -> "image" to Paragraph.ImageInfo::class.java
            is Paragraph.VideoInfo -> "video" to Paragraph.VideoInfo::class.java
            is Paragraph.Text -> "text" to Paragraph.Text::class.java
            is Paragraph.Quote -> "quote" to Paragraph.Quote::class.java
            is Paragraph.ReplyTo -> "reply" to Paragraph.ReplyTo::class.java
            is Paragraph.Link -> "link" to Paragraph.Link::class.java
            is Paragraph.RichText -> "rich_text" to Paragraph.RichText::class.java
        }
        addProperty("kind", kind)
        add("value", context.serialize(source, concrete))
    }

    override fun deserialize(
        json: JsonElement,
        type: java.lang.reflect.Type,
        context: JsonDeserializationContext,
    ): Paragraph {
        val objectValue = json.asJsonObject
        val value = objectValue.get("value") ?: throw JsonParseException("Missing paragraph value")
        val concrete = when (objectValue.get("kind")?.asString) {
            "image" -> Paragraph.ImageInfo::class.java
            "video" -> Paragraph.VideoInfo::class.java
            "text" -> Paragraph.Text::class.java
            "quote" -> Paragraph.Quote::class.java
            "reply" -> Paragraph.ReplyTo::class.java
            "link" -> Paragraph.Link::class.java
            "rich_text" -> Paragraph.RichText::class.java
            else -> throw JsonParseException("Unknown paragraph kind")
        }
        return context.deserialize(value, concrete)
    }
}

data class ThreadSummariesRequest(val board: Board, val page: Int)
data class ThreadPageRequest(val summary: ThreadSummary, val pageToken: String?)
data class CommentPageRequest(val post: Post, val page: Int)
data class WebUrlRequest(val summary: ThreadSummary)
data class BoardWebUrlRequest(val board: Board)

/** Bounded, versioned capability description returned by an isolated Source after binding. */
data class SourceRuntimeDescriptor(
    val protocolVersion: Int,
    val sourceId: String,
    val name: String,
    val language: String,
    val sourceVersion: Int,
    val iconUrl: String?,
    val supportsCommentPagination: Boolean,
    val alwaysUseRawImage: Boolean,
    /** Source behavior flag. Authentication capability is independently derived from [webCookieAuth]. */
    val needsLogin: Boolean,
    val webCookieAuth: WebCookieAuthDescriptor?,
    val webLoginUserAgent: String?,
)

/** Wire representation of every field in [AuthSpec.WebCookie]. */
data class WebCookieAuthDescriptor(
    val loginUrl: String,
    val allowedHosts: Set<String>,
    val cookieOrigins: Set<String>,
    val cookieDomains: Set<String>,
    val javaScriptEnabled: Boolean,
)

/**
 * Base class used directly by extension APK services. Every concrete service supplies exactly
 * one [Source] and runs in its own `isolatedProcess`; no extension class enters the Host process.
 */
abstract class IsolatedSourceService : Service() {
    protected abstract val source: Source

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val operationMutex = Mutex()

    private val binder = object : ISourceService.Stub() {
        override fun execute(
            requestId: Long,
            operation: Int,
            request: ParcelFileDescriptor,
            callback: ISourceCallback,
            broker: IHostBroker,
        ) {
            if (jobs.containsKey(requestId)) {
                request.closeQuietly()
                sendResult(requestId, ExtensionProtocol.STATUS_INVALID_REQUEST, "duplicate request", callback)
                return
            }
            val job = scope.launch {
                try {
                    withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                        val requestJson = PipePayload.readUtf8(request, ExtensionProtocol.MAX_CONTROL_BYTES)
                        val result = operationMutex.withLock {
                            val operationSource = source
                            if (operationSource is SessionAwareSource) {
                                operationSource.onAttach(BrokerRuntime(broker))
                            }
                            executeSourceOperation(operationSource, operation, requestJson)
                        }
                        sendResult(requestId, ExtensionProtocol.STATUS_OK, result, callback)
                    }
                } catch (_: TimeoutCancellationException) {
                    sendFailure(
                        requestId,
                        SourceFailure(SourceFailureCode.TIMED_OUT, operationName(operation)),
                        callback,
                    )
                } catch (_: CancellationException) {
                    sendResult(requestId, ExtensionProtocol.STATUS_CANCELLED, "cancelled", callback)
                } catch (_: PayloadTooLargeException) {
                    sendResult(requestId, ExtensionProtocol.STATUS_PAYLOAD_TOO_LARGE, "payload too large", callback)
                } catch (error: SourceFailureException) {
                    sendFailure(requestId, error.failure, callback)
                } catch (_: IllegalArgumentException) {
                    sendFailure(
                        requestId,
                        SourceFailure(SourceFailureCode.PARSER_CONTRACT, operationName(operation)),
                        callback,
                    )
                } catch (error: Exception) {
                    sendFailure(requestId, SourceFailures.fromThrowable(error, operationName(operation)), callback)
                } finally {
                    jobs.remove(requestId)
                    request.closeQuietly()
                }
            }
            jobs[requestId] = job
        }

        override fun cancel(requestId: Long) {
            jobs.remove(requestId)?.cancel()
        }

        override fun close() {
            jobs.values.forEach(Job::cancel)
            jobs.clear()
        }
    }

    final override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private suspend fun executeSourceOperation(source: Source, operation: Int, request: String): String =
    when (operation) {
        ExtensionProtocol.OP_RUNTIME_DESCRIPTOR -> {
            ExtensionWireJson.decode<Unit>(request)
            ExtensionWireJson.encode(source.toRuntimeDescriptor()).also { descriptor ->
                require(descriptor.toByteArray(Charsets.UTF_8).size <= ExtensionProtocol.MAX_DESCRIPTOR_BYTES) {
                    "Runtime descriptor is too large"
                }
            }
        }
        ExtensionProtocol.OP_BOARD_CATEGORIES -> ExtensionWireJson.encode(source.getBoardCategories())
        ExtensionProtocol.OP_BOARD_PAGE -> ExtensionWireJson.encode(
            source.getBoardPage(ExtensionWireJson.decode<BoardPageRequest>(request)),
        )
        ExtensionProtocol.OP_THREAD_SUMMARIES -> ExtensionWireJson.decode<ThreadSummariesRequest>(request).let {
            require(it.page > 0) { "page must be positive" }
            ExtensionWireJson.encode(source.getThreadSummaries(it.board, it.page))
        }
        ExtensionProtocol.OP_THREAD -> ExtensionWireJson.encode(
            source.getThread(ExtensionWireJson.decode(request)),
        )
        ExtensionProtocol.OP_THREAD_PAGE -> ExtensionWireJson.decode<ThreadPageRequest>(request).let {
            ExtensionWireJson.encode(source.getThreadPage(it.summary, it.pageToken))
        }
        ExtensionProtocol.OP_COMMENTS -> ExtensionWireJson.decode<CommentPageRequest>(request).let {
            require(it.page > 0) { "page must be positive" }
            ExtensionWireJson.encode(source.getComments(it.post, it.page))
        }
        ExtensionProtocol.OP_WEB_URL -> ExtensionWireJson.encode(
            source.getWebUrl(ExtensionWireJson.decode<WebUrlRequest>(request).summary),
        )
        ExtensionProtocol.OP_BOARD_WEB_URL -> ExtensionWireJson.encode(
            source.getBoardWebUrl(ExtensionWireJson.decode<BoardWebUrlRequest>(request).board),
        )
        ExtensionProtocol.OP_VALIDATE_SESSION -> {
            require(source is AuthenticatedSource) { "Source does not support authentication" }
            ExtensionWireJson.encode(source.validateSession())
        }
        else -> throw IllegalArgumentException("Unknown operation: $operation")
    }

internal fun Source.toRuntimeDescriptor(): SourceRuntimeDescriptor {
    val webCookie = when (val spec = (this as? AuthenticatedSource)?.authSpec) {
        null, AuthSpec.None -> null
        is AuthSpec.WebCookie -> WebCookieAuthDescriptor(
            loginUrl = spec.loginUrl,
            allowedHosts = spec.allowedHosts,
            cookieOrigins = spec.cookieOrigins,
            cookieDomains = spec.cookieDomains,
            javaScriptEnabled = spec.javaScriptEnabled,
        )
    }
    return SourceRuntimeDescriptor(
        protocolVersion = ExtensionProtocol.VERSION,
        sourceId = id,
        name = name,
        language = language,
        sourceVersion = version,
        iconUrl = iconUrl,
        supportsCommentPagination = supportsCommentPagination,
        alwaysUseRawImage = alwaysUseRawImage,
        needsLogin = needsLogin,
        webCookieAuth = webCookie,
        webLoginUserAgent = (this as? WebLoginUserAgentProvider)?.webLoginUserAgent,
    )
}

private class BrokerRuntime(broker: IHostBroker) : SourceRuntime {
    override val network: SourceNetwork = BinderSourceNetwork(broker)
    override val namedCookies: NamedCookieCapability = BinderNamedCookieCapability(broker)
    override val authentication: AuthenticationSession = object : AuthenticationSession {
        override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.Unknown)
        override fun markExpired() = Unit
    }
}

private class BinderNamedCookieCapability(private val broker: IHostBroker) : NamedCookieCapability {
    private val requestIds = AtomicLong(0L)

    override suspend fun hasPttAdultConsent(): Boolean =
        call(ExtensionProtocol.COOKIE_OP_PTT_ADULT_CONSENT_STATUS, Unit)

    override suspend fun storeEynyChallengeProof(proof: EynyChallengeProof) {
        check(call<Boolean>(ExtensionProtocol.COOKIE_OP_EYNY_CHALLENGE_PROOF, proof))
    }

    private suspend inline fun <reified Result> call(operation: Int, request: Any): Result {
        val requestId = requestIds.decrementAndGet()
        val payload = ExtensionWireJson.encode(request)
        require(payload.toByteArray(Charsets.UTF_8).size <= ExtensionProtocol.MAX_CONTROL_BYTES) {
            "Named cookie request is too large"
        }
        val pipe = PipePayload.writeUtf8(payload)
        return try {
            withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : IHostBrokerCallback.Stub() {
                        override fun onResult(id: Long, status: Int, response: ParcelFileDescriptor) {
                            if (id != requestId || !continuation.isActive) {
                                response.closeQuietly()
                                return
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    val json = PipePayload.readUtf8(response, ExtensionProtocol.MAX_CONTROL_BYTES)
                                    when (status) {
                                        ExtensionProtocol.STATUS_OK -> Unit
                                        ExtensionProtocol.STATUS_SOURCE_FAILURE ->
                                            throw SourceFailureException(SourceFailureWire.decode(json))
                                        ExtensionProtocol.STATUS_CANCELLED -> throw CancellationException("cancelled")
                                        else -> throw SourceFailureException(
                                            SourceFailure(SourceFailureCode.EXTENSION_RUNTIME),
                                        )
                                    }
                                    ExtensionWireJson.decode<Result>(json)
                                }.onSuccess(continuation::resume)
                                    .onFailure(continuation::resumeWithException)
                            }
                        }
                    }
                    continuation.invokeOnCancellation { runCatching { broker.cancel(requestId) } }
                    runCatching {
                        broker.executeNamedCookieOperation(requestId, operation, pipe, callback)
                    }.onFailure(continuation::resumeWithException)
                }
            }
        } finally {
            pipe.closeQuietly()
        }
    }
}

private class BinderSourceNetwork(private val broker: IHostBroker) : SourceNetwork {
    private val requestIds = AtomicLong()

    override suspend fun execute(request: SourceNetworkRequest): SourceNetworkResponse {
        val requestId = requestIds.incrementAndGet()
        val json = ExtensionWireJson.encode(request)
        require(json.toByteArray(Charsets.UTF_8).size <= ExtensionProtocol.MAX_NETWORK_REQUEST_BYTES) {
            "Network request is too large"
        }
        val pipe = PipePayload.writeUtf8(json)
        return try {
            withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : IHostBrokerCallback.Stub() {
                        override fun onResult(id: Long, status: Int, payload: ParcelFileDescriptor) {
                            if (id != requestId || !continuation.isActive) {
                                payload.closeQuietly()
                                return
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    val response = PipePayload.readUtf8(
                                        payload,
                                        ExtensionProtocol.MAX_NETWORK_RESPONSE_BYTES,
                                    )
                                    when (status) {
                                        ExtensionProtocol.STATUS_OK -> Unit
                                        ExtensionProtocol.STATUS_SOURCE_FAILURE ->
                                            throw SourceFailureException(SourceFailureWire.decode(response))
                                        ExtensionProtocol.STATUS_CANCELLED -> throw CancellationException("cancelled")
                                        ExtensionProtocol.STATUS_PAYLOAD_TOO_LARGE -> throw SourceFailureException(
                                            SourceFailure(SourceFailureCode.PAYLOAD_TOO_LARGE),
                                        )
                                        else -> throw SourceFailureException(
                                            SourceFailure(SourceFailureCode.EXTENSION_RUNTIME),
                                        )
                                    }
                                    ExtensionWireJson.decode<SourceNetworkResponse>(response)
                                }.onSuccess(continuation::resume)
                                    .onFailure(continuation::resumeWithException)
                            }
                        }
                    }
                    continuation.invokeOnCancellation { runCatching { broker.cancel(requestId) } }
                    try {
                        broker.execute(requestId, pipe, callback)
                    } catch (error: RemoteException) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        } finally {
            pipe.closeQuietly()
        }
    }
}

object PipePayload {
    suspend fun readUtf8(descriptor: ParcelFileDescriptor, maxBytes: Int): String = withContext(Dispatchers.IO) {
        val stat = runCatching { Os.fstat(descriptor.fileDescriptor) }
            .getOrElse { error ->
                descriptor.closeQuietly()
                throw IOException("Unable to inspect IPC descriptor", error)
            }
        if (!OsConstants.S_ISFIFO(stat.st_mode)) {
            descriptor.closeQuietly()
            throw IOException("IPC payload must be a one-way pipe")
        }
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val result = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw PayloadTooLargeException()
                result.write(buffer, 0, read)
            }
            result.toString(Charsets.UTF_8.name())
        }
    }

    fun writeUtf8(value: String): ParcelFileDescriptor {
        val bytes = value.toByteArray(Charsets.UTF_8)
        // Reliable-pipe status sockets report STATUS_DEAD spuriously for one-way Binder callbacks
        // on Android 14 isolated processes. The AIDL status field already carries bounded errors;
        // a kernel pipe keeps the payload one-way/FIFO without a second lifetime channel.
        val pipe = ParcelFileDescriptor.createPipe()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) }
            }.onFailure { pipe[1].closeQuietly() }
        }
        return pipe[0]
    }
}

private class PayloadTooLargeException : IOException()

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}

private fun sendResult(requestId: Long, status: Int, value: String, callback: ISourceCallback) {
    val bounded = value.toByteArray(Charsets.UTF_8).let { bytes ->
        if (bytes.size <= ExtensionProtocol.MAX_RESULT_BYTES) value else "result too large"
    }
    val finalStatus = if (bounded === value) status else ExtensionProtocol.STATUS_PAYLOAD_TOO_LARGE
    val pipe = PipePayload.writeUtf8(bounded)
    runCatching { callback.onResult(requestId, finalStatus, pipe) }
    pipe.closeQuietly()
}

private fun sendFailure(requestId: Long, failure: SourceFailure, callback: ISourceCallback) {
    sendResult(requestId, ExtensionProtocol.STATUS_SOURCE_FAILURE, SourceFailureWire.encode(failure), callback)
}

internal fun operationName(operation: Int): String = when (operation) {
    ExtensionProtocol.OP_BOARD_CATEGORIES -> "board_categories"
    ExtensionProtocol.OP_BOARD_PAGE -> "board_page"
    ExtensionProtocol.OP_THREAD_SUMMARIES -> "thread_summaries"
    ExtensionProtocol.OP_THREAD -> "thread"
    ExtensionProtocol.OP_THREAD_PAGE -> "thread_page"
    ExtensionProtocol.OP_COMMENTS -> "comments"
    ExtensionProtocol.OP_WEB_URL -> "web_url"
    ExtensionProtocol.OP_VALIDATE_SESSION -> "validate_session"
    ExtensionProtocol.OP_BOARD_WEB_URL -> "board_web_url"
    ExtensionProtocol.OP_RUNTIME_DESCRIPTOR -> "runtime_descriptor"
    else -> "unknown"
}
