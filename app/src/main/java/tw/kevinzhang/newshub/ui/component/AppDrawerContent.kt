package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.navigation.NavItems
import tw.kevinzhang.newshub.ui.navigation.drawerNavItems
import tw.kevinzhang.newshub.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    currentRoute: String,
    topNavItems: List<NavItems>,
    onTopNavItemClick: (NavItems) -> Unit = { },
    bottomNavItems: List<NavItems>,
    onBottomNavItemClick: (NavItems) -> Unit = { },
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                AppDrawerHeader(name = "NewsHub")
                topNavItems.forEach {
                    NavigationDrawerItem(
                        label = { NavItemText(it) },
                        icon = { Icon(painterResource(id = it.icon), null) },
                        selected = currentRoute == it.route,
                        onClick = { onTopNavItemClick(it) },
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(id = R.dimen.space_1))
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }

            Column {
                bottomNavItems.forEach {
                    NavigationDrawerItem(
                        label = { NavItemText(it) },
                        icon = { Icon(painterResource(id = it.icon), null) },
                        selected = currentRoute == it.route,
                        onClick = { onBottomNavItemClick(it) },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimensionResource(id = R.dimen.space_1))
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }
}

@Composable
fun AppDrawerHeader(
    name: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(id = R.dimen.space_16))
    ) {
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_100)))
        Text(text = name, style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_2)))
    }
}

@Preview
@Composable
private fun PreviewDrawer() {
    NewshubTheme {
        AppDrawerContent(
            currentRoute = "",
            topNavItems = drawerNavItems(),
            bottomNavItems = drawerNavItems(),
        )
    }
}

@Composable
fun NavItemText(item: NavItems) {
    Text(text = item.resourceId?.takeIf { it != 0 }?.let { stringResource(id = it) } ?: item.title ?: item.route)
}