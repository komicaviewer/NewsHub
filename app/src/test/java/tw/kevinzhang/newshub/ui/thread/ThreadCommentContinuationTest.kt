package tw.kevinzhang.newshub.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.CommentContinuation
import tw.kevinzhang.extension_api.model.CommentPage
import tw.kevinzhang.extension_api.model.Paragraph

class ThreadCommentContinuationTest {
    @Test
    fun `forest preserves nested order and attaches continuation to its parent`() {
        val forest = buildCommentForest(
            comments = listOf(
                comment("root"),
                comment("child", parentId = "root"),
                comment("grandchild", parentId = "child"),
            ),
            continuations = listOf(CommentContinuation("more-child", parentId = "child", remainingCount = 2)),
        )

        val root = forest.single() as CommentForestItem.Node
        val child = root.children.single() as CommentForestItem.Node
        assertEquals("root", root.comment.id)
        assertEquals("child", child.comment.id)
        assertEquals("grandchild", (child.children[0] as CommentForestItem.Node).comment.id)
        assertEquals("more-child", (child.children[1] as CommentForestItem.More).continuation.token)
    }

    @Test
    fun `forest keeps missing parents and cycles visible exactly once`() {
        val forest = buildCommentForest(
            comments = listOf(
                comment("orphan", parentId = "missing"),
                comment("a", parentId = "b"),
                comment("b", parentId = "a"),
            ),
            continuations = listOf(CommentContinuation("missing-branch", parentId = "missing")),
        )

        val ids = forest.flatMap(::commentIds)
        assertEquals(listOf("orphan", "a", "b"), ids)
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(forest.any { it is CommentForestItem.More && it.continuation.token == "missing-branch" })
    }

    @Test
    fun `continuation merge replaces loaded token and deduplicates replayed results`() {
        val merged = mergeCommentContinuation(
            currentComments = listOf(comment("root")),
            currentContinuations = listOf(
                CommentContinuation("loaded", parentId = "root"),
                CommentContinuation("untouched"),
            ),
            currentHasMore = false,
            loadedToken = "loaded",
            result = CommentPage(
                comments = listOf(comment("root"), comment("child", parentId = "root")),
                hasMore = true,
                continuations = listOf(
                    CommentContinuation("next", parentId = "child"),
                    CommentContinuation("untouched"),
                ),
            ),
        )

        assertEquals(listOf("root", "child"), merged.comments.map(Comment::id))
        assertEquals(listOf("untouched", "next"), merged.continuations.map(CommentContinuation::token))
        assertFalse(merged.continuations.any { it.token == "loaded" })
        assertTrue(merged.hasMore)
    }

    private fun comment(id: String, parentId: String? = null) = Comment(
        id = id,
        author = "tester",
        createdAt = 1L,
        content = listOf(Paragraph.Text(id)),
        parentId = parentId,
    )

    private fun commentIds(item: CommentForestItem): List<String> = when (item) {
        is CommentForestItem.More -> emptyList()
        is CommentForestItem.Node -> listOf(item.comment.id) + item.children.flatMap(::commentIds)
    }
}
