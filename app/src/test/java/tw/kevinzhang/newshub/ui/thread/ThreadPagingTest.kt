package tw.kevinzhang.newshub.ui.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Post

class ThreadPagingTest {
    @Test
    fun `merged pages retain the first post version and append only new IDs`() {
        val result = mergePostsById(
            existing = listOf(post("one", "first"), post("two", "existing")),
            incoming = listOf(
                post("two", "replacement"),
                post("three", "new"),
                post("three", "duplicate in page"),
            ),
        )

        assertEquals(listOf("one", "two", "three"), result.map(Post::id))
        assertEquals("existing", result.single { it.id == "two" }.author)
    }

    @Test
    fun `next page token advances even when a page has no new posts`() {
        val initial = ThreadPagingState().forInitialPage("page-1")
        val completed = initial.startAppend()!!.appendSucceeded("page-1", "page-2")

        assertTrue(completed.hasMore)
        assertEquals("page-2", completed.nextPageToken)
        assertFalse(completed.isAppending)
    }

    @Test
    fun `self or cycle token ends pagination`() {
        val initial = ThreadPagingState().forInitialPage("page-1")
        val self = initial.startAppend()!!.appendSucceeded("page-1", "page-1")
        assertFalse(self.hasMore)
        assertNull(self.nextPageToken)

        val advanced = initial.startAppend()!!.appendSucceeded("page-1", "page-2")
        val cycle = advanced.startAppend()!!.appendSucceeded("page-2", "page-1")
        assertFalse(cycle.hasMore)
        assertNull(cycle.nextPageToken)
    }

    @Test
    fun `blank token ends pagination`() {
        val completed = ThreadPagingState()
            .forInitialPage("page-1")
            .startAppend()!!
            .appendSucceeded("page-1", "  ")

        assertFalse(completed.hasMore)
        assertNull(completed.nextPageToken)
    }

    @Test
    fun `opaque token is preserved exactly`() {
        val opaqueToken = " page=2&cursor=a+b "

        val initial = ThreadPagingState().forInitialPage(opaqueToken)

        assertEquals(opaqueToken, initial.nextPageToken)
    }

    @Test
    fun `failed append keeps token so retry can use it`() {
        val failed = ThreadPagingState()
            .forInitialPage("page-1")
            .startAppend()!!
            .appendFailed("network")

        assertTrue(failed.canAppend)
        assertEquals("page-1", failed.nextPageToken)
        assertEquals("network", failed.appendError)
    }

    private fun post(id: String, author: String) = Post(
        id = id,
        author = author,
        createdAt = null,
        thumbnail = null,
        content = emptyList(),
        comments = emptyList(),
    )
}
