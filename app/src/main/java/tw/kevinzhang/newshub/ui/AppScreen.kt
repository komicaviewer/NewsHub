package tw.kevinzhang.newshub.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import kotlinx.coroutines.launch
import tw.kevinzhang.extension_api.model.ThreadSummary
import tw.kevinzhang.newshub.auth.AuthViewModel
import tw.kevinzhang.newshub.encode
import tw.kevinzhang.newshub.ui.boards.BoardGroupDetailScreen
import tw.kevinzhang.newshub.ui.boards.BoardsScreen
import tw.kevinzhang.newshub.ui.auth.AuthWebViewScreen
import tw.kevinzhang.newshub.ui.collection.BoardPickerScreen
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineScreen
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineViewModel
import tw.kevinzhang.newshub.ui.collection.CreateCollectionScreen
import tw.kevinzhang.newshub.ui.collection.CreateCollectionViewModel
import tw.kevinzhang.newshub.ui.collection.EditCollectionScreen
import tw.kevinzhang.newshub.ui.collection.EditCollectionViewModel
import tw.kevinzhang.newshub.ui.collection.ManageCollectionsScreen
import tw.kevinzhang.newshub.ui.collection.SelectedBoard
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.AppBottomBar
import tw.kevinzhang.newshub.ui.component.AppDrawer
import tw.kevinzhang.newshub.ui.history.ReadingHistoryScreen
import tw.kevinzhang.newshub.ui.marketplace.ManageReposScreen
import tw.kevinzhang.newshub.ui.marketplace.MarketplaceScreen
import tw.kevinzhang.newshub.ui.navigation.MainNavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.savedposts.SavedPostDetailScreen
import tw.kevinzhang.newshub.ui.savedposts.SavedPostsScreen
import tw.kevinzhang.newshub.ui.settings.SettingsScreen
import tw.kevinzhang.newshub.ui.settings.ReadingPreferencesScreen
import tw.kevinzhang.newshub.ui.theme.NewshubTheme
import tw.kevinzhang.newshub.ui.thread.ThreadDetailScreen

private const val THREAD_DETAIL_ROUTE =
    "thread_detail?threadId={threadId}&sourceId={sourceId}&boardUrl={boardUrl}" +
        "&threadTitle={threadTitle}&boardName={boardName}"

private const val AUTH_WEB_LOGIN_ROUTE = "auth_web_login"

private fun ThreadSummary.threadDetailRoute(boardName: String? = null): String {
    val encodedThreadId = id.encode()
    val encodedSourceId = sourceId.encode()
    val encodedBoardUrl = boardUrl.encode()
    val encodedTitle = title?.encode() ?: ""
    val encodedBoardName = boardName?.encode() ?: ""
    return "thread_detail?threadId=$encodedThreadId&sourceId=$encodedSourceId" +
        "&boardUrl=$encodedBoardUrl&threadTitle=$encodedTitle&boardName=$encodedBoardName"
}

private fun NavGraphBuilder.threadDetailDestination(
    onNavigateUp: () -> Unit,
    onNavigateToBoards: () -> Unit,
) {
    composable(
        route = THREAD_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("threadId") { type = NavType.StringType },
            navArgument("sourceId") { type = NavType.StringType },
            navArgument("boardUrl") { type = NavType.StringType },
            navArgument("threadTitle") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("boardName") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        val context = LocalContext.current
        ThreadDetailScreen(
            onNavigateUp = onNavigateUp,
            onNavigateToBoards = onNavigateToBoards,
            onOpenWebClick = { url ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bindAppScreen(navController: NavHostController = rememberNavController()) {
    val appViewModel: AppViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val navItems = remember { mainNavItems() }

    val webLoginUiState by authViewModel.webLoginUiState.collectAsStateWithLifecycle()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val currentDestination = currentBackStack?.destination
    val isHomeRoute = currentRoute == "home"
    val isCollectionRoute = currentRoute == "collection/{collectionId}"

    val showBottomBar = navItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    val selectedTab = navItems.firstOrNull { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    } ?: MainNavItems.Collections

    val defaultCollectionId by appViewModel.defaultCollectionId.collectAsStateWithLifecycle()

    var collectionScrollToTopTrigger by remember { mutableIntStateOf(0) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val openDrawer = { coroutineScope.launch { drawerState.open() } }

    LaunchedEffect(webLoginUiState.request, currentRoute) {
        if (webLoginUiState.request != null && currentRoute != AUTH_WEB_LOGIN_ROUTE) {
            navController.navigate(AUTH_WEB_LOGIN_ROUTE) {
                launchSingleTop = true
            }
        } else if (webLoginUiState.request == null && currentRoute == AUTH_WEB_LOGIN_ROUTE) {
            navController.popBackStack()
        }
    }

    NewshubTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = isCollectionRoute || isHomeRoute,
            drawerContent = {
                AppDrawer(
                    onCollectionClick = { collection ->
                        appViewModel.selectCollection(collection.id)
                        navController.navigate("collection/${collection.id}") {
                            popUpTo("collection/{collectionId}") { inclusive = true }
                            launchSingleTop = false
                        }
                        coroutineScope.launch { drawerState.close() }
                    },
                    onCreateCollectionClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("create_collection")
                    },
                    onManageCollectionsClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("manage_collections")
                    },
                )
            },
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    if (showBottomBar) {
                        AppBottomBar(
                            navItems = navItems,
                            selectedItem = selectedTab,
                            onNavItemClick = { item ->
                                if (item == MainNavItems.Collections && isCollectionRoute) {
                                    collectionScrollToTopTrigger++
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = MainNavItems.Collections.route,
                    modifier = Modifier
                        .padding(padding)
                        .then(
                            if (currentRoute == AUTH_WEB_LOGIN_ROUTE) Modifier
                            else Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                        ),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    composable(AUTH_WEB_LOGIN_ROUTE) {
                        val request = webLoginUiState.request
                        if (request != null) {
                            AuthWebViewScreen(
                                request = request,
                                isVerifying = webLoginUiState.isVerifying,
                                errorMessage = webLoginUiState.errorMessage,
                                onFinishLogin = authViewModel::completeWebLogin,
                                onCancelLogin = { authViewModel.cancelLogin(request.sourceId) },
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    navigation(
                        route = MainNavItems.Collections.route,
                        startDestination = "home",
                    ) {
                        composable("home") {
                            val homeDefaultCollectionId by appViewModel.defaultCollectionId.collectAsStateWithLifecycle()
                            var navigatedToDefault by remember { mutableStateOf(false) }

                            LaunchedEffect(homeDefaultCollectionId) {
                                if (!navigatedToDefault && homeDefaultCollectionId != null) {
                                    navigatedToDefault = true
                                    navController.navigate("collection/$homeDefaultCollectionId")
                                }
                            }

                            if (defaultCollectionId == null) {
                                Scaffold(
                                    topBar = {
                                        TopAppBar(
                                            title = {},
                                            navigationIcon = {
                                                IconButton(onClick = { openDrawer() }) {
                                                    Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                                                }
                                            },
                                        )
                                    },
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BodyLargeText(
                                            text = "Swipe right or tap \u2630 to select a collection",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        composable(
                            route = "collection/{collectionId}",
                            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val useTwoPane = maxWidth >= 840.dp
                                if (useTwoPane) {
                                    val detailNavController = rememberNavController()
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Box(modifier = Modifier.weight(0.42f)) {
                                            CollectionTimelineScreen(
                                                onOpenDrawer = { openDrawer() },
                                                scrollToTopTrigger = collectionScrollToTopTrigger,
                                                onNavigateToBoards = {
                                                    navController.navigate(MainNavItems.Boards.route)
                                                },
                                                onNavigateToBoardPicker = {
                                                    navController.navigate("board_picker/collection/$collectionId")
                                                },
                                                onThreadClick = { summary, boardName ->
                                                    detailNavController.navigate(
                                                        summary.threadDetailRoute(boardName),
                                                    ) {
                                                        popUpTo("detail_empty")
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )
                                        }
                                        VerticalDivider()
                                        Box(modifier = Modifier.weight(0.58f)) {
                                            NavHost(
                                                navController = detailNavController,
                                                startDestination = "detail_empty",
                                                enterTransition = { EnterTransition.None },
                                                exitTransition = { ExitTransition.None },
                                                popEnterTransition = { EnterTransition.None },
                                                popExitTransition = { ExitTransition.None },
                                            ) {
                                                composable("detail_empty") {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        BodyLargeText(
                                                            text = "選擇一篇貼文開始閱讀",
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                                threadDetailDestination(
                                                    onNavigateUp = {
                                                        detailNavController.popBackStack(
                                                            route = "detail_empty",
                                                            inclusive = false,
                                                        )
                                                    },
                                                    onNavigateToBoards = {
                                                        navController.navigate(MainNavItems.Boards.route)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    CollectionTimelineScreen(
                                        onOpenDrawer = { openDrawer() },
                                        scrollToTopTrigger = collectionScrollToTopTrigger,
                                        onNavigateToBoards = {
                                            navController.navigate(MainNavItems.Boards.route)
                                        },
                                        onNavigateToBoardPicker = {
                                            navController.navigate("board_picker/collection/$collectionId")
                                        },
                                        onThreadClick = { summary, boardName ->
                                            navController.navigate(summary.threadDetailRoute(boardName))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    threadDetailDestination(
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToBoards = { navController.navigate(MainNavItems.Boards.route) },
                    )
                    composable("boards") {
                        BoardsScreen(
                            onNavigateToMarketplace = { navController.navigate("marketplace") },
                            onNavigateToGroupDetail = { sourceId ->
                                navController.navigate("board_group/${sourceId.encode()}")
                            },
                            onLoginClick = { sourceId -> authViewModel.triggerLogin(sourceId) },
                            onLogoutClick = { sourceId -> authViewModel.logout(sourceId) },
                        )
                    }
                    composable(
                        route = "board_group/{sourceId}",
                        arguments = listOf(navArgument("sourceId") { type = NavType.StringType }),
                    ) { backStackEntry ->
                        BoardGroupDetailScreen(
                            sourceId = backStackEntry.arguments?.getString("sourceId").orEmpty(),
                            onNavigateUp = { navController.navigateUp() },
                            onLoginClick = { sourceId -> authViewModel.triggerLogin(sourceId) },
                            onLogoutClick = { sourceId -> authViewModel.logout(sourceId) },
                        )
                    }
                    composable("marketplace") {
                        MarketplaceScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onNavigateToManageRepos = { navController.navigate("manage_repos") },
                        )
                    }
                    composable("manage_repos") {
                        ManageReposScreen(
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                    navigation(
                        route = MainNavItems.Settings.route,
                        startDestination = "settings_home",
                    ) {
                        composable("settings_home") {
                            SettingsScreen(
                                onNavigateToReadingHistory = { navController.navigate("reading_history") },
                                onNavigateToSavedPosts = { navController.navigate("saved_posts") },
                                onNavigateToReadingPreferences = {
                                    navController.navigate("reading_preferences")
                                },
                            )
                        }
                        composable("reading_preferences") {
                            ReadingPreferencesScreen(onNavigateUp = { navController.navigateUp() })
                        }
                        composable("reading_history") {
                            ReadingHistoryScreen(
                                onNavigateUp = { navController.navigateUp() },
                                onThreadClick = { summary ->
                                    navController.navigate(summary.threadDetailRoute())
                                },
                            )
                        }
                        composable("saved_posts") {
                            SavedPostsScreen(
                                onNavigateUp = { navController.navigateUp() },
                                onThreadClick = { entity ->
                                    val sourceId = entity.sourceId.encode()
                                    val threadId = entity.threadId.encode()
                                    navController.navigate("saved_post_detail?sourceId=$sourceId&threadId=$threadId")
                                },
                            )
                        }
                        composable(
                            route = "saved_post_detail?sourceId={sourceId}&threadId={threadId}",
                            arguments = listOf(
                                navArgument("sourceId") { type = NavType.StringType },
                                navArgument("threadId") { type = NavType.StringType },
                            ),
                        ) {
                            val context = LocalContext.current
                            SavedPostDetailScreen(
                                onNavigateUp = { navController.navigateUp() },
                                onOpenWebClick = { url ->
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            )
                        }
                    }
                    composable("create_collection") {
                        CreateCollectionScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onCollectionCreated = { collectionId ->
                                navController.navigate("collection/$collectionId") {
                                    popUpTo("create_collection") { inclusive = true }
                                }
                            },
                            onNavigateToBoardPicker = { navController.navigate("board_picker/create") },
                        )
                    }
                    composable("board_picker/create") {
                        val parentEntry = remember(it) { navController.getBackStackEntry("create_collection") }
                        val createVM: CreateCollectionViewModel = hiltViewModel(parentEntry)
                        val selectedBoards by createVM.selectedBoards.collectAsStateWithLifecycle()
                        BoardPickerScreen(
                            selectedBoards = selectedBoards,
                            onBoardToggle = createVM::toggleBoard,
                            onConfirm = { navController.navigateUp() },
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                    composable("manage_collections") {
                        ManageCollectionsScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onEditCollection = { id -> navController.navigate("edit_collection/$id") },
                        )
                    }
                    composable(
                        route = "edit_collection/{collectionId}",
                        arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                    ) {
                        val collectionId = it.arguments?.getString("collectionId") ?: ""
                        EditCollectionScreen(
                            onNavigateUp = { navController.navigateUp() },
                            onNavigateToBoardPicker = { navController.navigate("board_picker/edit/$collectionId") },
                        )
                    }
                    composable(
                        route = "board_picker/edit/{collectionId}",
                        arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                    ) {
                        val parentEntry = remember(it) { navController.getBackStackEntry("edit_collection/{collectionId}") }
                        val editVM: EditCollectionViewModel = hiltViewModel(parentEntry)
                        val selectedBoards by editVM.selectedBoards.collectAsStateWithLifecycle()
                        BoardPickerScreen(
                            selectedBoards = selectedBoards,
                            onBoardToggle = editVM::toggleBoard,
                            onConfirm = { navController.navigateUp() },
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                    composable(
                        route = "board_picker/collection/{collectionId}",
                        arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                    ) {
                        val parentEntry = remember(it) { navController.getBackStackEntry("collection/{collectionId}") }
                        val collectionVM: CollectionTimelineViewModel = hiltViewModel(parentEntry)
                        var selectedBoards by remember { mutableStateOf(emptySet<SelectedBoard>()) }
                        BoardPickerScreen(
                            selectedBoards = selectedBoards,
                            onBoardToggle = { board ->
                                selectedBoards = if (board in selectedBoards) selectedBoards - board else selectedBoards + board
                            },
                            onConfirm = {
                                selectedBoards.forEach { board ->
                                    collectionVM.addBoardSubscription(board.sourceId, board.boardUrl, board.boardName)
                                }
                                navController.navigateUp()
                            },
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                }
            }
        }
    }
}
