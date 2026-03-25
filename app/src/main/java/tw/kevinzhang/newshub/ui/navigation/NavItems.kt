package tw.kevinzhang.newshub.ui.navigation

import tw.kevinzhang.newshub.R

open class NavItems(
    open val resourceId: Int? = null,
    open val icon: Int,
    open val route: String,
    open val title: String? = null,
)

sealed class MainNavItems(
    override val icon: Int,
    override val route: String,
    val labelRes: Int,
) : NavItems(resourceId = labelRes, icon = icon, route = route) {
    object Collections : MainNavItems(R.drawable.ic_outline_home_24, "collections", R.string.collections)
    object Extensions : MainNavItems(R.drawable.ic_outline_globe_24, "extensions", R.string.extensions)
    object Settings : MainNavItems(R.drawable.ic_outline_settings_24, "settings", R.string.settings)
}

fun mainNavItems() = listOf(MainNavItems.Collections, MainNavItems.Extensions, MainNavItems.Settings)