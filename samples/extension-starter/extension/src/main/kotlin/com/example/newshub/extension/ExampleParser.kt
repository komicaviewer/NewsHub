package com.example.newshub.extension

import com.google.gson.Gson
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.extension_api.model.Thread
import tw.kevinzhang.extension_api.model.ThreadSummary

internal class ExampleParser(private val gson: Gson = Gson()) {
    fun boards(bytes: ByteArray): List<Board> = gson.fromJson(bytes.toString(Charsets.UTF_8), BoardEnvelope::class.java)
        .boards.map { Board(SOURCE_ID, it.url, it.name, it.description) }

    fun summaries(bytes: ByteArray, board: Board): List<ThreadSummary> =
        gson.fromJson(bytes.toString(Charsets.UTF_8), SummaryEnvelope::class.java).threads.map { item ->
            ThreadSummary(
                sourceId = SOURCE_ID,
                boardUrl = board.url,
                id = item.id,
                title = item.title,
                author = item.author,
                createdAt = item.createdAt,
                commentCount = item.commentCount,
                rawImage = null,
                thumbnail = null,
                previewContent = listOf(Paragraph.Text(item.preview)),
            )
        }

    fun thread(bytes: ByteArray): Thread {
        val item = gson.fromJson(bytes.toString(Charsets.UTF_8), ThreadPayload::class.java)
        return Thread(
            id = item.id,
            url = item.url,
            title = item.title,
            posts = item.posts.map { post ->
                Post(
                    id = post.id,
                    author = post.author,
                    createdAt = post.createdAt,
                    thumbnail = null,
                    content = listOf(Paragraph.Text(post.text)),
                    comments = emptyList(),
                )
            },
        )
    }

    private data class BoardEnvelope(val boards: List<BoardPayload>)
    private data class BoardPayload(val url: String, val name: String, val description: String?)
    private data class SummaryEnvelope(val threads: List<SummaryPayload>)
    private data class SummaryPayload(
        val id: String,
        val title: String,
        val author: String?,
        val createdAt: Long?,
        val commentCount: Int?,
        val preview: String,
    )
    private data class ThreadPayload(val id: String, val url: String?, val title: String?, val posts: List<PostPayload>)
    private data class PostPayload(val id: String, val author: String?, val createdAt: Long?, val text: String)

    companion object {
        const val SOURCE_ID = "com.example.news"
    }
}
