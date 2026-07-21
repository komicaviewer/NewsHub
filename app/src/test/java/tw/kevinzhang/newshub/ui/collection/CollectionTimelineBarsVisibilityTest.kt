package tw.kevinzhang.newshub.ui.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionTimelineBarsVisibilityTest {
    @Test
    fun `upward content scroll hides bars after threshold`() {
        val tracker = BarsVisibilityScrollTracker(threshold = 12f)

        assertNull(tracker.onScroll(-5f))
        assertEquals(false, tracker.onScroll(-7f))
    }

    @Test
    fun `downward content scroll shows bars after threshold`() {
        val tracker = BarsVisibilityScrollTracker(threshold = 12f)

        assertNull(tracker.onScroll(8f))
        assertEquals(true, tracker.onScroll(4f))
    }

    @Test
    fun `direction change resets accumulated scroll`() {
        val tracker = BarsVisibilityScrollTracker(threshold = 12f)

        assertNull(tracker.onScroll(-8f))
        assertNull(tracker.onScroll(8f))
        assertEquals(true, tracker.onScroll(4f))
    }
}
