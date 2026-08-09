package tw.kevinzhang.extension_loader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import tw.kevinzhang.extension_api.AuthSpec
import tw.kevinzhang.extension_api.AuthenticatedSource
import tw.kevinzhang.extension_api.CommentPageRequest
import tw.kevinzhang.extension_api.ExtensionProtocol
import tw.kevinzhang.extension_api.ExtensionWireJson
import tw.kevinzhang.extension_api.HostBrokerProvider
import tw.kevinzhang.extension_api.HostResourceProvider
import tw.kevinzhang.extension_api.IHostBroker
import tw.kevinzhang.extension_api.ISourceCallback
import tw.kevinzhang.extension_api.ISourceService
import tw.kevinzhang.extension_api.PipePayload
import tw.kevinzhang.extension_api.Source
import tw.kevinzhang.extension_api.SourceIdentity
import tw.kevinzhang.extension_api.ThreadPageRequest
import tw.kevinzhang.extension_api.ThreadSummariesRequest
import tw.kevinzhang.extension_api.WebUrlRequest
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardCategory
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadPage
import tw.kevinzhang.extension_api.model.ThreadSummary
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class RemoteSourceConnection(
    context: Context,
    descriptor: ExtensionDescriptor,
) : ServiceConnection {
    private val appContext = context.applicationContext
    private val component = ComponentName(descriptor.packageName, descriptor.serviceClassName)
    private val service = MutableStateFlow<ISourceService?>(null)
    private var bound = false

    fun bind(): Boolean {
        if (bound) return true
        bound = appContext.bindService(
            Intent(ExtensionProtocol.SERVICE_ACTION).setComponent(component),
            this,
            Context.BIND_AUTO_CREATE,
        )
        return bound
    }

    suspend fun awaitService(): ISourceService = withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
        service.filterNotNull().first()
    }

    fun close() {
        service.value?.close()
        service.value = null
        if (bound) runCatching { appContext.unbindService(this) }
        bound = false
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service.value = ISourceService.Stub.asInterface(binder)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service.value = null
    }

    override fun onBindingDied(name: ComponentName) {
        service.value = null
        bound = false
    }

    override fun onNullBinding(name: ComponentName) {
        service.value = null
    }
}

internal open class RemoteSource(
    protected val descriptor: ExtensionDescriptor,
    private val signerSha256: String,
    private val policy: OfficialSourcePolicy,
    private val connection: RemoteSourceConnection,
    brokerProvider: HostBrokerProvider,
    private val resourceProvider: HostResourceProvider,
) : Source {
    private val requestIds = AtomicLong()
    private val broker: IHostBroker = brokerProvider.brokerFor(
        SourceIdentity(descriptor.packageName, signerSha256, descriptor.sourceId),
        policy.networkPolicy(),
    )

    final override val id: String = descriptor.sourceId
    final override val name: String = descriptor.name
    final override val language: String = descriptor.lang
    final override val version: Int = ExtensionProtocol.VERSION
    final override val iconUrl: String? = null
    final override val supportsCommentPagination: Boolean = true
    final override val alwaysUseRawImage: Boolean = false
    final override val needsLogin: Boolean = descriptor.needsLogin

    override suspend fun getBoardCategories(): List<BoardCategory> =
        call(ExtensionProtocol.OP_BOARD_CATEGORIES, Unit)

    override suspend fun getBoardPage(request: BoardPageRequest): BoardPage =
        call(ExtensionProtocol.OP_BOARD_PAGE, request)

    override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> =
        call<ThreadSummariesRequest, List<ThreadSummary>>(
            ExtensionProtocol.OP_THREAD_SUMMARIES,
            ThreadSummariesRequest(board, page),
        )
            .map(::protect)

    override suspend fun getThread(summary: ThreadSummary): Thread =
        protect(call<ThreadSummary, Thread>(ExtensionProtocol.OP_THREAD, summary))

    override suspend fun getThreadPage(summary: ThreadSummary, pageToken: String?): ThreadPage =
        protect(
            call<ThreadPageRequest, ThreadPage>(
                ExtensionProtocol.OP_THREAD_PAGE,
                ThreadPageRequest(summary, pageToken),
            ),
        )

    override suspend fun getComments(post: Post, page: Int): CommentPage =
        call<CommentPageRequest, CommentPage>(
            ExtensionProtocol.OP_COMMENTS,
            CommentPageRequest(post, page),
        ).let { pageResult ->
            pageResult.copy(comments = pageResult.comments.map(::protect))
        }

    override suspend fun getWebUrl(summary: ThreadSummary): String? =
        // Raw extension links are not Host capabilities. Link handles will use a separate,
        // user-gesture-only contract; until then this path is deliberately unavailable.
        null

    protected suspend inline fun <reified Request, reified Response> call(
        operation: Int,
        request: Request,
    ): Response {
        val requestId = requestIds.incrementAndGet()
        val service = connection.awaitService()
        val requestJson = ExtensionWireJson.encode(request)
        require(requestJson.toByteArray().size <= ExtensionProtocol.MAX_CONTROL_BYTES) {
            "Extension request is too large"
        }
        val pipe = PipePayload.writeUtf8(requestJson)
        return try {
            withTimeout(ExtensionProtocol.REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : ISourceCallback.Stub() {
                        override fun onResult(id: Long, status: Int, payload: ParcelFileDescriptor) {
                            if (id != requestId || !continuation.isActive) {
                                runCatching { payload.close() }
                                return
                            }
                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                runCatching {
                                    val result = PipePayload.readUtf8(payload, ExtensionProtocol.MAX_RESULT_BYTES)
                                    when (status) {
                                        ExtensionProtocol.STATUS_OK -> ExtensionWireJson.decode<Response>(result)
                                        ExtensionProtocol.STATUS_PAYLOAD_TOO_LARGE -> throw IOException("Extension result exceeded limit")
                                        ExtensionProtocol.STATUS_CANCELLED -> throw kotlinx.coroutines.CancellationException(result)
                                        else -> throw IOException("Extension request failed: $result")
                                    }
                                }.onSuccess(continuation::resume)
                                    .onFailure(continuation::resumeWithException)
                            }
                        }
                    }
                    continuation.invokeOnCancellation { runCatching { service.cancel(requestId) } }
                    runCatching { service.execute(requestId, operation, pipe, callback, broker) }
                        .onFailure(continuation::resumeWithException)
                }
            }
        } finally {
            runCatching { pipe.close() }
        }
    }

    private fun protect(thread: Thread): Thread = thread.copy(
        url = null,
        posts = thread.posts.map(::protect),
    )

    private fun protect(page: ThreadPage): ThreadPage = page.copy(
        posts = page.posts.map(::protect),
        metadata = page.metadata?.copy(url = null),
    )

    private fun protect(summary: ThreadSummary): ThreadSummary = summary.copy(
        rawImage = summary.rawImage?.let(::resourceModel),
        thumbnail = summary.thumbnail?.let(::resourceModel),
        previewContent = summary.previewContent.map(::protect),
        sourceIconUrl = summary.sourceIconUrl?.let(::resourceModel),
    )

    private fun protect(post: Post): Post = post.copy(
        thumbnail = post.thumbnail?.let(::resourceModel),
        content = post.content.map(::protect),
        comments = post.comments.map(::protect),
        rawHtml = null,
        sourceIconUrl = post.sourceIconUrl?.let(::resourceModel),
    )

    private fun protect(comment: Comment): Comment = comment.copy(content = comment.content.map(::protect))

    private fun protect(paragraph: Paragraph): Paragraph = when (paragraph) {
        is Paragraph.ImageInfo -> paragraph.copy(
            thumb = paragraph.thumb?.let(::resourceModel),
            raw = resourceModel(paragraph.raw),
        )
        is Paragraph.VideoInfo -> paragraph.copy(url = resourceModel(paragraph.url))
        is Paragraph.RichText -> paragraph.copy(
            runs = paragraph.runs.map { it.copy(linkUrl = null) },
        )
        is Paragraph.Link -> Paragraph.Text(paragraph.content)
        else -> paragraph
    }

    private fun resourceModel(untrustedUrl: String): String = runCatching {
        resourceProvider.issueResource(
            SourceIdentity(descriptor.packageName, signerSha256, descriptor.sourceId),
            policy.networkPolicy(),
            untrustedUrl,
        ).asModel()
    }.getOrDefault("newshub-blocked://resource")
}

internal class RemoteAuthenticatedSource(
    descriptor: ExtensionDescriptor,
    signerSha256: String,
    policy: OfficialSourcePolicy,
    connection: RemoteSourceConnection,
    brokerProvider: HostBrokerProvider,
    resourceProvider: HostResourceProvider,
) : RemoteSource(
    descriptor,
    signerSha256,
    policy,
    connection,
    brokerProvider,
    resourceProvider,
), AuthenticatedSource {
    override val authSpec: AuthSpec = AuthSpec.WebCookie(
        loginUrl = requireNotNull(descriptor.loginUrl),
        allowedHosts = descriptor.loginHosts,
        cookieOrigins = descriptor.loginHosts.mapTo(linkedSetOf()) { "https://$it/" },
    )

    override suspend fun validateSession(): Boolean =
        call<Unit, Boolean>(ExtensionProtocol.OP_VALIDATE_SESSION, Unit)
}
