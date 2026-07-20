package tw.kevinzhang.newshub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPreferencesTest {

    @Test
    fun `stored values map to their corresponding display modes`() {
        assertEquals(TimelineDisplayMode.MEDIA_FIRST, TimelineDisplayMode.fromStoredValue("MEDIA_FIRST"))
        assertEquals(ReplyDisplayMode.NESTED, ReplyDisplayMode.fromStoredValue("NESTED"))
        assertEquals(ReadTrackingMode.THREAD_OPENED, ReadTrackingMode.fromStoredValue("THREAD_OPENED"))
    }

    @Test
    fun `missing or invalid stored values use product defaults`() {
        assertEquals(TimelineDisplayMode.COMPACT, TimelineDisplayMode.fromStoredValue(null))
        assertEquals(TimelineDisplayMode.COMPACT, TimelineDisplayMode.fromStoredValue("grid"))
        assertEquals(ReplyDisplayMode.CONTEXTUAL, ReplyDisplayMode.fromStoredValue("tree"))
        assertEquals(ReadTrackingMode.POST_VISIBLE, ReadTrackingMode.fromStoredValue("on_open"))
    }
}
