package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import tw.kevinzhang.newshub.ui.navigation.*
import tw.kevinzhang.newshub.ui.theme.NewshubTheme

@Composable
fun AppBottomBar(
    navItems: List<NavItems>,
    onNavItemClick: (NavItems) -> Unit = { },
    selectedItem : NavItems? = null,
) {
    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem (
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = item.route
                    )
                },
                selected = selectedItem == item,
                onClick = { onNavItemClick(item) },
                label = { NavItemText(item = item) }
            )
        }
    }
}

@Preview
@Composable
fun PreviewAppBottomBar() {
    NewshubTheme {
        AppBottomBar(
            navItems = bottomNavItems(),
        )
    }
}