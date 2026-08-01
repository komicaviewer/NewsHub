package tw.kevinzhang.newshub.ui.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CollectionTimelineStateInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collection_reselect_scrolls_the_destination_state_to_top_once() {
        lateinit var timelineState: CollectionTimelineState

        composeRule.setContent {
            timelineState = rememberCollectionTimelineState(collectionId = "collection-a")
            val coroutineScope = rememberCoroutineScope()
            Column {
                Button(onClick = {
                    coroutineScope.launch { timelineState.listState.scrollToItem(50, 24) }
                }) {
                    Text("scroll to middle")
                }
                Button(onClick = {
                    coroutineScope.launch { timelineState.scrollToTop() }
                }) {
                    Text("reselect collections")
                }
                LazyColumn(state = timelineState.listState) {
                    items((0..99).toList()) { item -> Text("thread $item") }
                }
            }
        }

        composeRule.onNodeWithText("scroll to middle").performClick()
        composeRule.runOnIdle {
            assertEquals(50, timelineState.listState.firstVisibleItemIndex)
            timelineState.updateBarsVisibility(false)
            assertFalse(timelineState.barsVisible)
        }

        composeRule.onNodeWithText("reselect collections").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle {
            assertEquals(0, timelineState.listState.firstVisibleItemIndex)
            assertEquals(0, timelineState.listState.firstVisibleItemScrollOffset)
            assertTrue(timelineState.barsVisible)
        }
    }
}
