package tw.kevinzhang.newshub.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post

class ReplyThreadingTest {
    @Test
    fun `threaded posts use depth first order while retaining actual depth`() {
        val posts = listOf(
            post("op"),
            post("sibling", "op"),
            post("child", "sibling"),
            post("grandchild", "child"),
            post("great-grandchild", "grandchild"),
            post("second-root"),
        )

        val result = posts.asThreadedPosts(maxDepth = 9)

        assertEquals(
            listOf("op", "sibling", "child", "grandchild", "great-grandchild", "second-root"),
            result.map { it.post.id },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 0), result.map(ThreadedPost::actualDepth))
        assertEquals(listOf(0, 1, 2, 3, 3, 0), result.map(ThreadedPost::visualDepth))
        assertEquals(listOf(null, "op", "sibling", "child", "grandchild", null), result.map(ThreadedPost::parentId))
    }

    @Test
    fun `threading connects replies after posts from separate pages are combined`() {
        // A later page can contain both a descendant and an earlier parent. Building from the
        // combined list keeps the branch together instead of treating the descendant as a root.
        val firstPage = listOf(post("op"), post("first-reply", "op"))
        val nextPage = listOf(post("deep-reply", "first-reply"), post("second-reply", "op"))

        val result = (firstPage + nextPage).asThreadedPosts(maxDepth = 3)

        assertEquals(
            listOf("op", "first-reply", "deep-reply", "second-reply"),
            result.map { it.post.id },
        )
        assertEquals(listOf(0, 1, 2, 1), result.map(ThreadedPost::actualDepth))
        assertEquals(listOf(0, 1, 2, 1), result.map(ThreadedPost::visualDepth))
    }

    @Test
    fun `missing targets and cycles never hide posts`() {
        val posts = listOf(
            post("missing", "not-in-thread"),
            post("a", "b"),
            post("b", "a"),
        )

        assertEquals(listOf("missing", "a", "b"), posts.asThreadedPosts().map { it.post.id })
    }

    @Test
    fun `visible fraction is based on overlap with viewport`() {
        assertEquals(0f, visibleFraction(0, 100, 100, 300), 0.001f)
        assertEquals(0.5f, visibleFraction(50, 100, 100, 300), 0.001f)
        assertEquals(1f, visibleFraction(120, 100, 100, 300), 0.001f)
        assertEquals(1f, visibleFraction(0, 400, 0, 200), 0.001f)
        assertEquals(0.5f, visibleFraction(100, 400, 0, 200), 0.001f)
    }

    private fun post(id: String, replyTo: String? = null) = Post(
        id = id,
        author = null,
        createdAt = null,
        thumbnail = null,
        content = replyTo?.let { listOf(Paragraph.ReplyTo(it)) }.orEmpty(),
        comments = emptyList(),
    )
}
