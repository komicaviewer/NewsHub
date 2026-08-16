package com.example.newshub.extension

import tw.kevinzhang.extension_api.SessionAwareSource
import tw.kevinzhang.extension_api.SourceRuntime
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.BoardPage
import tw.kevinzhang.extension_api.model.BoardPageRequest
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

class ExampleSource internal constructor(
    private val parser: ExampleParser = ExampleParser(),
    private var api: ExampleApi? = null,
) : SessionAwareSource {
    override val id = ExampleParser.SOURCE_ID
    override val name = "Example News"
    override val language = "en"
    override val version = 1
    override val iconUrl: String? = null
    override val supportsCommentPagination = false
    override val alwaysUseRawImage = false
    override val needsLogin = false

    override fun onAttach(runtime: SourceRuntime) {
        api = ExampleApi(BrokerNetworkAdapter(runtime.network))
    }

    override suspend fun getBoardPage(request: BoardPageRequest): BoardPage {
        require(request.pageToken == null) { "Example News has one board page" }
        val boards = parser.boards(requireApi().boards())
            .filter { request.query.text.isBlank() || it.name.contains(request.query.text, ignoreCase = true) }
            .take(request.pageSize)
        return BoardPage(boards)
    }

    override suspend fun getThreadSummaries(board: Board, page: Int): List<ThreadSummary> {
        require(page > 0) { "page must be 1-based" }
        return parser.summaries(requireApi().summaries(board.url, page), board)
    }

    override suspend fun getThread(summary: ThreadSummary): Thread =
        parser.thread(requireApi().thread(summary.id))

    override suspend fun getWebUrl(summary: ThreadSummary): String =
        "https://www.example.com/thread/${summary.id}"

    private fun requireApi(): ExampleApi = checkNotNull(api) { "Host runtime is not attached" }
}

class ExampleApi(private val broker: BrokerNetworkAdapter) {
    suspend fun boards(): ByteArray = broker.get("https://api.example.com/v1/boards").body

    suspend fun summaries(boardUrl: String, page: Int): ByteArray {
        val boardId = boardUrl.substringAfterLast('/')
        return broker.get("https://api.example.com/v1/boards/$boardId/threads?page=$page").body
    }

    suspend fun thread(id: String): ByteArray =
        broker.get("https://api.example.com/v1/threads/$id").body
}
