package tw.kevinzhang.extension_api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.CommentContinuation
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.ThreadFeedFilter
import tw.kevinzhang.extension_api.model.ThreadFeedFilterOption
import tw.kevinzhang.extension_api.model.ThreadSummaryPageRequest

class FeedAndCommentContractTest {
    @Test fun `feed filter dimensions and opaque cursor round trip on the wire`() {
        val request = ThreadSummaryPageRequest(
            board = Board("reddit", "https://reddit.com/r/androiddev", "AndroidDev", null),
            filters = mapOf("sort" to "top", "time" to "week"),
            pageToken = "opaque-after-token",
            pageSize = 50,
        )

        assertEquals(
            request,
            ExtensionWireJson.decode<ThreadSummaryPageRequest>(ExtensionWireJson.encode(request)),
        )
    }

    @Test fun `feed dimensions require a declared unique default`() {
        val options = listOf(
            ThreadFeedFilterOption("hot", "Hot"),
            ThreadFeedFilterOption("new", "New"),
        )
        val filter = ThreadFeedFilter("sort", "Sort", options, "hot")
        assertEquals("hot", filter.defaultOptionId)

        assertInvalid { ThreadFeedFilter("sort", "Sort", options, "top") }
        assertInvalid { ThreadFeedFilter("sort", "Sort", options + options.first(), "hot") }
    }

    @Test fun `nested comment parent and continuation round trip on the wire`() {
        val page = CommentPage(
            comments = listOf(Comment("child", "reader", 123L, emptyList(), parentId = "parent")),
            hasMore = true,
            continuations = listOf(CommentContinuation("opaque-more-token", "parent", 17)),
        )

        val decoded: CommentPage = ExtensionWireJson.decode(ExtensionWireJson.encode(page))
        assertEquals("parent", decoded.comments.single().parentId)
        assertEquals("opaque-more-token", decoded.continuations.single().token)
        assertTrue(decoded.hasMore)
    }

    private fun assertInvalid(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }
}
