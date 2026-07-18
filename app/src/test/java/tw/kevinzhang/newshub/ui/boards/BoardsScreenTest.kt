package tw.kevinzhang.newshub.ui.boards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Board

class BoardsScreenTest {
    @Test
    fun `group preview keeps original order and appends more after five boards`() {
        val boards = (1..7).map(::board)

        val items = buildBoardGroupItems(boards)

        assertEquals(6, items.size)
        assertEquals(
            boards.take(5),
            items.filterIsInstance<BoardGroupItem.BoardCard>().map { it.board },
        )
        assertTrue(items.last() is BoardGroupItem.More)
    }

    @Test
    fun `group with five or fewer boards does not show more`() {
        val items = buildBoardGroupItems((1..5).map(::board))

        assertEquals(5, items.size)
        assertFalse(items.any { it is BoardGroupItem.More })
    }

    private fun board(index: Int) = Board(
        sourceId = "source",
        url = "https://example.com/$index",
        name = "Board $index",
    )
}
