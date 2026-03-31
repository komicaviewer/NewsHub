package tw.kevinzhang.newshub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import tw.kevinzhang.newshub.R

open class NavItems(
    open val resourceId: Int? = null,
    open val icon: ImageVector,
    open val selectedIcon: ImageVector = icon,
    open val route: String,
    open val title: String? = null,
)

sealed class MainNavItems(
    override val icon: ImageVector,
    override val selectedIcon: ImageVector,
    override val route: String,
    val labelRes: Int,
) : NavItems(resourceId = labelRes, icon = icon, selectedIcon = selectedIcon, route = route) {
    object Collections : MainNavItems(Icons.Outlined.Home, Icons.Filled.Home, "collections", R.string.collections)
    object Extensions : MainNavItems(Icons.Outlined.Language, Icons.Filled.Language, "extensions", R.string.extensions)
    object Settings : MainNavItems(Icons.Outlined.Settings, Icons.Filled.Settings, "settings", R.string.settings)
}

fun mainNavItems() = listOf(MainNavItems.Collections, MainNavItems.Extensions, MainNavItems.Settings)
