package tw.kevinzhang.newshub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.newshub.ui.component.APP_BOTTOM_BAR_TEST_TAG
import tw.kevinzhang.newshub.ui.navigation.MainNavItems
import tw.kevinzhang.newshub.ui.navigation.NavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.theme.NewshubTheme

@RunWith(AndroidJUnit4::class)
class AppBottomBarOverlayInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collectionsAndBoardsShareExactlyOneBottomBarDuringRouteChanges() {
        val isCollectionRoute = mutableStateOf(true)
        val selectedTab = mutableStateOf<NavItems>(MainNavItems.Collections)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NewshubTheme {
                Box(Modifier.fillMaxSize()) {
                    AppBottomBarOverlay(
                        isCollectionRoute = isCollectionRoute.value,
                        collectionBarsVisible = true,
                        navItems = mainNavItems(),
                        selectedTab = selectedTab.value,
                        onNavItemClick = {},
                        onHeightChanged = {},
                        modifier = Modifier,
                    )
                }
            }
        }

        assertExactlyOneBottomBarForFrames()

        composeRule.runOnIdle {
            isCollectionRoute.value = false
            selectedTab.value = MainNavItems.Boards
        }
        assertExactlyOneBottomBarForFrames()

        composeRule.runOnIdle {
            isCollectionRoute.value = true
            selectedTab.value = MainNavItems.Collections
        }
        assertExactlyOneBottomBarForFrames()

        repeat(20) { index ->
            composeRule.runOnIdle {
                isCollectionRoute.value = index % 2 == 0
                selectedTab.value = if (isCollectionRoute.value) {
                    MainNavItems.Collections
                } else {
                    MainNavItems.Boards
                }
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag(APP_BOTTOM_BAR_TEST_TAG).assertCountEquals(1)
        }
    }

    @Test
    fun collectionScrollHideAndShowNeverCreatesAnotherBottomBar() {
        val barsVisible = mutableStateOf(true)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            NewshubTheme {
                Box(Modifier.fillMaxSize()) {
                    AppBottomBarOverlay(
                        isCollectionRoute = true,
                        collectionBarsVisible = barsVisible.value,
                        navItems = mainNavItems(),
                        selectedTab = MainNavItems.Collections,
                        onNavItemClick = {},
                        onHeightChanged = {},
                    )
                }
            }
        }

        assertExactlyOneBottomBarForFrames()
        composeRule.runOnIdle { barsVisible.value = false }
        repeat(16) {
            composeRule.mainClock.advanceTimeByFrame()
            assertAtMostOneBottomBar()
        }
        composeRule.onAllNodesWithTag(APP_BOTTOM_BAR_TEST_TAG).assertCountEquals(0)

        composeRule.runOnIdle { barsVisible.value = true }
        repeat(16) {
            composeRule.mainClock.advanceTimeByFrame()
            assertAtMostOneBottomBar()
        }
        composeRule.onAllNodesWithTag(APP_BOTTOM_BAR_TEST_TAG).assertCountEquals(1)
    }

    private fun assertExactlyOneBottomBarForFrames() {
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithTag(APP_BOTTOM_BAR_TEST_TAG).assertCountEquals(1)
        }
    }

    private fun assertAtMostOneBottomBar() {
        val count = composeRule.onAllNodesWithTag(APP_BOTTOM_BAR_TEST_TAG)
            .fetchSemanticsNodes()
            .size
        assertTrue("Expected at most one bottom bar, found $count", count <= 1)
    }
}
