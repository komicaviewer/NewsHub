package tw.kevinzhang.newshub.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.orhanobut.logger.Logger
import kotlinx.coroutines.launch
import tw.kevinzhang.newshub.encode
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineScreen
import tw.kevinzhang.newshub.ui.component.AppBottomBar
import tw.kevinzhang.newshub.ui.component.AppDrawer
import tw.kevinzhang.newshub.ui.extensions.ExtensionsScreen
import tw.kevinzhang.newshub.ui.marketplace.MarketplaceScreen
import tw.kevinzhang.newshub.ui.navigation.MainNavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.theme.NewshubTheme
import tw.kevinzhang.newshub.ui.thread.ThreadDetailScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bindAppScreen(navController: NavHostController = rememberNavController()) {
    val appViewModel: AppViewModel = hiltViewModel()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val showBottomBar = currentRoute in setOf(
        "home",
        "collection/{collectionId}",
        "extensions",
        "settings",
    )

    val selectedTab = when {
        currentRoute == "home" || currentRoute == "collection/{collectionId}" -> MainNavItems.Collections
        currentRoute == "extensions" -> MainNavItems.Extensions
        currentRoute == "settings" -> MainNavItems.Settings
        else -> MainNavItems.Collections
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val openDrawer = { coroutineScope.launch { drawerState.open() } }

    val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

    NewshubTheme {
        val systemUiController = rememberSystemUiController()
        val backgroundColor = MaterialTheme.colorScheme.background
        SideEffect {
            systemUiController.setSystemBarsColor(color = backgroundColor)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    onCollectionClick = { collection ->
                        appViewModel.selectCollection(collection.id)
                        navController.navigate("collection/${collection.id}") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        AppBottomBar(
                            navItems = mainNavItems(),
                            scrollBehavior = scrollBehavior,
                            selectedItem = selectedTab,
                            onNavItemClick = { item ->
                                val route =
                                    if (item == MainNavItems.Collections) "home" else item.route
                                navController.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier
                        .padding(padding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable("home") {
                        val defaultCollectionId by appViewModel.defaultCollectionId.collectAsStateWithLifecycle()
                        var navigatedToDefault by remember { mutableStateOf(false) }

                        LaunchedEffect(defaultCollectionId) {
                            if (!navigatedToDefault && defaultCollectionId != null) {
                                navigatedToDefault = true
                                navController.navigate("collection/$defaultCollectionId") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        }

                        if (defaultCollectionId == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Swipe right or tap \u2630 to select a collection",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    composable(
                        route = "collection/{collectionId}",
                        arguments = listOf(navArgument("collectionId") {
                            type = NavType.StringType
                        }),
                    ) {
                        CollectionTimelineScreen(
                            onOpenDrawer = { openDrawer() },
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
                        val context = LocalContext.current
                        ThreadDetailScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onOpenWebClick = { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            },
                        )
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
}
