package tw.kevinzhang.newshub.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kevinzhang.newshub.ui.navigation.MainNavItems

class AppBottomBarActionTest {
    @Test
    fun `collections opens the default collection outside a collection route`() {
        val action = resolveAppBottomBarAction(
            item = MainNavItems.Collections,
            currentRoute = "boards",
            defaultCollectionId = "default-id",
        )

        assertEquals(
            AppBottomBarAction.Navigate("collection/default-id"),
            action,
        )
    }

    @Test
    fun `collections opens home when the default collection is null or blank`() {
        assertEquals(
            AppBottomBarAction.Navigate("home"),
            resolveAppBottomBarAction(
                item = MainNavItems.Collections,
                currentRoute = "boards",
                defaultCollectionId = null,
            ),
        )
        assertEquals(
            AppBottomBarAction.Navigate("home"),
            resolveAppBottomBarAction(
                item = MainNavItems.Collections,
                currentRoute = "boards",
                defaultCollectionId = "   ",
            ),
        )
    }

    @Test
    fun `collections scrolls to top from an active collection`() {
        val action = resolveAppBottomBarAction(
            item = MainNavItems.Collections,
            currentRoute = "collection/{collectionId}",
            defaultCollectionId = "default-id",
        )

        assertEquals(AppBottomBarAction.ScrollCollectionToTop, action)
    }

    @Test
    fun `boards and settings navigate to their own routes`() {
        assertEquals(
            AppBottomBarAction.Navigate(MainNavItems.Boards.route),
            resolveAppBottomBarAction(
                item = MainNavItems.Boards,
                currentRoute = "collection/{collectionId}",
                defaultCollectionId = "default-id",
            ),
        )
        assertEquals(
            AppBottomBarAction.Navigate(MainNavItems.Settings.route),
            resolveAppBottomBarAction(
                item = MainNavItems.Settings,
                currentRoute = "collection/{collectionId}",
                defaultCollectionId = "default-id",
            ),
        )
    }
}
