package tw.kevinzhang.newshub.ui.boards

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BoardSearchInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchClearAndRefresh() {
        val query = mutableStateOf("")
        var refreshes = 0
        compose.setContent {
            MaterialTheme { BoardSearchControls(query.value, false, null, { query.value = it }, { refreshes++ }) }
        }
        compose.onNodeWithText("推薦看板").assertIsDisplayed()
        compose.onNodeWithText("搜尋此論壇看板…").performTextInput("手機")
        compose.onNodeWithText("搜尋結果").assertIsDisplayed()
        compose.onNodeWithContentDescription("清除搜尋").performClick()
        compose.onNodeWithText("推薦看板").assertIsDisplayed()
        compose.onNodeWithText("更新目錄").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals("", query.value) }
    }
}
