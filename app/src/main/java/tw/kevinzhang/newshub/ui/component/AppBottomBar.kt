package tw.kevinzhang.newshub.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.kevinzhang.newshub.ui.navigation.NavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.theme.NewshubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomBar(
    navItems: List<NavItems>,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    onNavItemClick: (NavItems) -> Unit = {},
    selectedItem: NavItems? = null,
) {
    BottomAppBar(
        scrollBehavior = scrollBehavior,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        navItems.forEach { item ->
            val selected = item == selectedItem
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onNavItemClick(item) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = item.icon),
                    contentDescription = item.route,
                    tint = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.resourceId?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun PreviewAppBottomBar() {
    NewshubTheme {
        AppBottomBar(navItems = mainNavItems())
    }
}
