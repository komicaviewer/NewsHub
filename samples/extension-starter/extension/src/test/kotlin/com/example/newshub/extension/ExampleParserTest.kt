package com.example.newshub.extension

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Paragraph

class ExampleParserTest {
    private val parser = ExampleParser()

    @Test
    fun parsesBoardAndSummaryContracts() {
        val board = parser.boards(
            """{"boards":[{"url":"https://api.example.com/boards/news","name":"News","description":"Daily"}]}"""
                .toByteArray(),
        ).single()
        val summary = parser.summaries(
            """{"threads":[{"id":"42","title":"Hello","author":"Ada","createdAt":1000,"commentCount":3,"preview":"Preview"}]}"""
                .toByteArray(),
            board,
        ).single()

        assertEquals("com.example.news", board.sourceId)
        assertEquals("42", summary.id)
        assertEquals(Paragraph.Text("Preview"), summary.previewContent.single())
    }

    @Test
    fun parsesThreadPostsWithoutAmbientAndroidState() {
        val thread = parser.thread(
            """{"id":"42","url":"https://www.example.com/thread/42","title":"Hello","posts":[{"id":"1","author":"Ada","createdAt":1000,"text":"Body"}]}"""
                .toByteArray(),
        )

        assertEquals("42", thread.id)
        assertEquals(Paragraph.Text("Body"), thread.posts.single().content.single())
    }
}
