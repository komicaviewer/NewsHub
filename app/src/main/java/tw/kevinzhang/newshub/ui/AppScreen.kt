package tw.kevinzhang.newshub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import tw.kevinzhang.newshub.encode
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineScreen
import tw.kevinzhang.newshub.ui.collection.CollectionsScreen
import tw.kevinzhang.newshub.ui.component.AppBottomBar
import tw.kevinzhang.newshub.ui.extensions.ExtensionsScreen
import tw.kevinzhang.newshub.ui.marketplace.MarketplaceScreen
import tw.kevinzhang.newshub.ui.navigation.MainNavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.theme.NewshubTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import tw.kevinzhang.newshub.ui.thread.ThreadDetailScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bindAppScreen(navController: NavHostController = rememberNavController()) {
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val selectedTab = mainNavItems().firstOrNull { currentRoute?.startsWith(it.route) == true }
        ?: MainNavItems.Collections

    NewshubTheme {
        val systemUiController = rememberSystemUiController()
        systemUiController.setSystemBarsColor(color = MaterialTheme.colorScheme.background)

        Scaffold(
            bottomBar = {
                AppBottomBar(
                    navItems = mainNavItems(),
                    selectedItem = selectedTab,
                    onNavItemClick = { item ->
                        navController.navigate(item.route) {
                            popUpTo("collections") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "collections",
                modifier = Modifier.padding(padding),
            ) {
                composable("collections") {
                    CollectionsScreen(
                        onCollectionClick = { collection ->
                            navController.navigate("collection/${collection.id.encode()}")
                        },
                    )
                }
                composable(
                    route = "collection/{collectionId}",
                    arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                ) {
                    CollectionTimelineScreen(
                        onThreadClick = { summary ->
                            val threadId = summary.id.encode()
                            val sourceId = summary.sourceId.encode()
                            val boardUrl = summary.boardUrl.encode()
                            val title = summary.title?.encode() ?: ""
                            navController.navigate(
                                "thread_detail?threadId=$threadId&sourceId=$sourceId&boardUrl=$boardUrl&threadTitle=$title"
                            )
                        },
                    )
                }
                composable(
                    route = "thread_detail?threadId={threadId}&sourceId={sourceId}&boardUrl={boardUrl}&threadTitle={threadTitle}",
                    arguments = listOf(
                        navArgument("threadId") { type = NavType.StringType },
                        navArgument("sourceId") { type = NavType.StringType },
                        navArgument("boardUrl") { type = NavType.StringType },
                        navArgument("threadTitle") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    ThreadDetailScreen(onNavigateUp = { navController.navigateUp() })
                }
                composable("extensions") {
                    ExtensionsScreen(onNavigateToMarketplace = { navController.navigate("marketplace") })
                }
                composable("marketplace") {
                    MarketplaceScreen()
                }
                composable("settings") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Settings — coming soon")
                    }
                }
            }
        }
    }
}
