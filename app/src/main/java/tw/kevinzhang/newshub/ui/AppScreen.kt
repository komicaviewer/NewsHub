package tw.kevinzhang.newshub.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineState
import tw.kevinzhang.newshub.ui.collection.CollectionTimelineViewModel
import tw.kevinzhang.newshub.ui.collection.CreateCollectionScreen
import tw.kevinzhang.newshub.ui.collection.CreateCollectionViewModel
import tw.kevinzhang.newshub.ui.collection.EditCollectionScreen
import tw.kevinzhang.newshub.ui.collection.EditCollectionViewModel
import tw.kevinzhang.newshub.ui.collection.ManageCollectionsScreen
import tw.kevinzhang.newshub.ui.collection.SelectedBoard
import tw.kevinzhang.newshub.ui.collection.rememberCollectionTimelineState
import tw.kevinzhang.newshub.ui.component.BodyLargeText
import tw.kevinzhang.newshub.ui.component.AppBottomBar
import tw.kevinzhang.newshub.ui.component.AppDrawer
import tw.kevinzhang.newshub.ui.history.ReadingHistoryScreen
import tw.kevinzhang.newshub.ui.marketplace.ManageReposScreen
import tw.kevinzhang.newshub.ui.marketplace.MarketplaceScreen
import tw.kevinzhang.newshub.ui.navigation.MainNavItems
import tw.kevinzhang.newshub.ui.navigation.NavItems
import tw.kevinzhang.newshub.ui.navigation.mainNavItems
import tw.kevinzhang.newshub.ui.savedposts.SavedPostDetailScreen
import tw.kevinzhang.newshub.ui.savedposts.SavedPostsScreen
import tw.kevinzhang.newshub.ui.settings.SettingsScreen
import tw.kevinzhang.newshub.ui.settings.ReadingPreferencesScreen
import tw.kevinzhang.newshub.ui.theme.NewshubTheme
import tw.kevinzhang.newshub.ui.thread.ThreadDetailScreen

private const val THREAD_DETAIL_ROUTE =
    "thread_detail?threadId={threadId}&sourceId={sourceId}&sourceKey={sourceKey}&boardUrl={boardUrl}" +
        "&threadTitle={threadTitle}&boardName={boardName}"

private const val AUTH_WEB_LOGIN_ROUTE = "auth_web_login"
private const val APP_BAR_ANIMATION_MILLIS = 220

private class CollectionBottomBarBinding {
    private var timelineState by mutableStateOf<CollectionTimelineState?>(null)

    val barsVisible: Boolean
        get() = timelineState?.barsVisible ?: true

    fun attach(state: CollectionTimelineState) {
        timelineState = state
    }

    fun detach(state: CollectionTimelineState) {
        if (timelineState === state) timelineState = null
    }

    suspend fun scrollToTop() {
        timelineState?.scrollToTop()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppBottomBarOverlay(
    isCollectionRoute: Boolean,
    collectionBarsVisible: Boolean,
    navItems: List<NavItems>,
    selectedTab: NavItems,
    onNavItemClick: (NavItems) -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.height > 0) onHeightChanged(size.height)
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = !isCollectionRoute || collectionBarsVisible,
            enter = if (isCollectionRoute) {
                slideInVertically(
                    animationSpec = tween(APP_BAR_ANIMATION_MILLIS),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(APP_BAR_ANIMATION_MILLIS))
            } else {
                EnterTransition.None
            },
            exit = if (isCollectionRoute) {
                slideOutVertically(
                    animationSpec = tween(APP_BAR_ANIMATION_MILLIS),
                    targetOffsetY = { it },
                ) + fadeOut(animationSpec = tween(APP_BAR_ANIMATION_MILLIS))
            } else {
                ExitTransition.None
            },
        ) {
            AppBottomBar(
                navItems = navItems,
                selectedItem = selectedTab,
                onNavItemClick = onNavItemClick,
            )
        }
    }
}

@Composable
private fun BottomBarPaddedContent(
    bottomBarHeight: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomBarHeight),
    ) {
        content()
    }
}

internal sealed class AppBottomBarAction {
    data object ScrollCollectionToTop : AppBottomBarAction()

    data class Navigate(val route: String) : AppBottomBarAction()
}

internal fun resolveAppBottomBarAction(
    item: NavItems,
    currentRoute: String?,
    defaultCollectionId: String?,
): AppBottomBarAction {
    if (item.route != MainNavItems.Collections.route) {
        return AppBottomBarAction.Navigate(item.route)
    }

    if (currentRoute == "collection/{collectionId}") {
        return AppBottomBarAction.ScrollCollectionToTop
    }

    val collectionId = defaultCollectionId?.takeIf(String::isNotBlank)
    return AppBottomBarAction.Navigate(
        route = collectionId?.let { "collection/$it" } ?: "home",
    )
}

private fun ThreadSummary.threadDetailRoute(sourceKey: String, boardName: String? = null): String {
    val encodedThreadId = id.encode()
    val encodedSourceId = sourceId.encode()
    val encodedSourceKey = sourceKey.encode()
    val encodedBoardUrl = boardUrl.encode()
    val encodedTitle = title?.encode() ?: ""
    val encodedBoardName = boardName?.encode() ?: ""
    return "thread_detail?threadId=$encodedThreadId&sourceId=$encodedSourceId&sourceKey=$encodedSourceKey" +
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
            navArgument("sourceKey") { type = NavType.StringType },
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
        ThreadDetailScreen(
            onNavigateUp = onNavigateUp,
            onNavigateToBoards = onNavigateToBoards,
        )
    }
}

/**
 * Collection-only UI state stays with this destination. In particular, reselecting Collections
 * invokes [CollectionTimelineState.scrollToTop] immediately instead of storing an event in the
 * app shell for a later composition to replay.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CollectionTimelineDestination(
    collectionId: String,
    navController: NavHostController,
    bottomOverlayHeight: Dp,
    bottomBarBinding: CollectionBottomBarBinding,
    onOpenDrawer: () -> Unit,
) {
    val timelineState = rememberCollectionTimelineState(collectionId)

    DisposableEffect(bottomBarBinding, timelineState) {
        bottomBarBinding.attach(timelineState)
        onDispose {
            bottomBarBinding.detach(timelineState)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useTwoPane = maxWidth >= 840.dp
            if (useTwoPane) {
                val detailNavController = rememberNavController()
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.42f)) {
                        CollectionTimelineScreen(
                            timelineState = timelineState,
                            onOpenDrawer = onOpenDrawer,
                            bottomOverlayHeight = bottomOverlayHeight,
                            onNavigateToBoards = {
                                navController.navigate(MainNavItems.Boards.route)
                            },
                            onNavigateToBoardPicker = {
                                navController.navigate("board_picker/collection/$collectionId")
                            },
                            onThreadClick = { sourceKey, summary, boardName ->
                                detailNavController.navigate(summary.threadDetailRoute(sourceKey, boardName)) {
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
                    timelineState = timelineState,
                    onOpenDrawer = onOpenDrawer,
                    bottomOverlayHeight = bottomOverlayHeight,
                    onNavigateToBoards = {
                        navController.navigate(MainNavItems.Boards.route)
                    },
                    onNavigateToBoardPicker = {
                        navController.navigate("board_picker/collection/$collectionId")
                    },
                    onThreadClick = { sourceKey, summary, boardName ->
                        navController.navigate(summary.threadDetailRoute(sourceKey, boardName))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bindAppScreen(navController: NavHostController = rememberNavController()) {
    val appViewModel: AppViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val navItems = remember { mainNavItems() }

    val webLoginUiState by authViewModel.webLoginUiState.collectAsStateWithLifecycle()
    val oauthLoginUiState by authViewModel.oauthLoginUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
    val bottomBarBinding = remember { CollectionBottomBarBinding() }
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomOverlayHeight = with(LocalDensity.current) { bottomOverlayHeightPx.toDp() }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val openDrawer = { coroutineScope.launch { drawerState.open() } }
    val onBottomBarItemClick: (NavItems) -> Unit = { item ->
        when (
            val action = resolveAppBottomBarAction(
                item = item,
                currentRoute = currentRoute,
                defaultCollectionId = defaultCollectionId,
            )
        ) {
            AppBottomBarAction.ScrollCollectionToTop -> {
                coroutineScope.launch { bottomBarBinding.scrollToTop() }
            }

            is AppBottomBarAction.Navigate -> {
                navController.navigate(action.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    LaunchedEffect(webLoginUiState.request, currentRoute) {
        if (webLoginUiState.request != null && currentRoute != AUTH_WEB_LOGIN_ROUTE) {
            navController.navigate(AUTH_WEB_LOGIN_ROUTE) {
                launchSingleTop = true
            }
        } else if (webLoginUiState.request == null && currentRoute == AUTH_WEB_LOGIN_ROUTE) {
            navController.popBackStack()
        }
    }

    LaunchedEffect(oauthLoginUiState.launch) {
        val launch = oauthLoginUiState.launch ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, launch.authorizationUri))
            authViewModel.consumeOAuthLaunch(launch.sourceId)
        } catch (_: ActivityNotFoundException) {
            authViewModel.reportOAuthLaunchFailure(launch.sourceId)
        }
    }

    LaunchedEffect(oauthLoginUiState.errorMessage) {
        val message = oauthLoginUiState.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        authViewModel.consumeOAuthError()
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
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0),
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = MainNavItems.Collections.route,
                        modifier = Modifier
                            .fillMaxSize()
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
                            LaunchedEffect(defaultCollectionId) {
                                defaultCollectionId
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { collectionId ->
                                        navController.navigate("collection/$collectionId") {
                                            launchSingleTop = true
                                        }
                                    }
                            }

                            if (defaultCollectionId.isNullOrBlank()) {
                                BottomBarPaddedContent(bottomOverlayHeight) {
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
                        }
                        composable(
                            route = "collection/{collectionId}",
                            arguments = listOf(navArgument("collectionId") { type = NavType.StringType }),
                        ) { backStackEntry ->
                            val collectionId = backStackEntry.arguments?.getString("collectionId") ?: ""
                            CollectionTimelineDestination(
                                collectionId = collectionId,
                                navController = navController,
                                bottomOverlayHeight = bottomOverlayHeight,
                                bottomBarBinding = bottomBarBinding,
                                onOpenDrawer = { openDrawer() },
                            )
                        }
                    }
                    threadDetailDestination(
                        onNavigateUp = { navController.navigateUp() },
                        onNavigateToBoards = { navController.navigate(MainNavItems.Boards.route) },
                    )
                    composable("boards") {
                        BottomBarPaddedContent(bottomOverlayHeight) {
                            BoardsScreen(
                                onNavigateToMarketplace = { navController.navigate("marketplace") },
                                onNavigateToGroupDetail = { sourceId ->
                                    navController.navigate("board_group/${sourceId.encode()}")
                                },
                                onLoginClick = { sourceId -> authViewModel.triggerLogin(sourceId) },
                                onLogoutClick = { sourceId -> authViewModel.logout(sourceId) },
                            )
                        }
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
                            BottomBarPaddedContent(bottomOverlayHeight) {
                                SettingsScreen(
                                    onNavigateToReadingHistory = { navController.navigate("reading_history") },
                                    onNavigateToSavedPosts = { navController.navigate("saved_posts") },
                                    onNavigateToReadingPreferences = {
                                        navController.navigate("reading_preferences")
                                    },
                                )
                            }
                        }
                        composable("reading_preferences") {
                            BottomBarPaddedContent(bottomOverlayHeight) {
                                ReadingPreferencesScreen(onNavigateUp = { navController.navigateUp() })
                            }
                        }
                        composable("reading_history") {
                            BottomBarPaddedContent(bottomOverlayHeight) {
                                ReadingHistoryScreen(
                                    onNavigateUp = { navController.navigateUp() },
                                    onThreadClick = { sourceKey, summary ->
                                        navController.navigate(summary.threadDetailRoute(sourceKey))
                                    },
                                )
                            }
                        }
                        composable("saved_posts") {
                            BottomBarPaddedContent(bottomOverlayHeight) {
                                SavedPostsScreen(
                                    onNavigateUp = { navController.navigateUp() },
                                    onThreadClick = { record ->
                                        val sourceKey = record.savedPost.sourceKey.encode()
                                        val threadId = record.savedPost.threadId.encode()
                                        navController.navigate(
                                            "saved_post_detail?sourceKey=$sourceKey&threadId=$threadId",
                                        )
                                    },
                                )
                            }
                        }
                        composable(
                            route = "saved_post_detail?sourceKey={sourceKey}&threadId={threadId}",
                            arguments = listOf(
                                navArgument("sourceKey") { type = NavType.StringType },
                                navArgument("threadId") { type = NavType.StringType },
                            ),
                        ) {
                            BottomBarPaddedContent(bottomOverlayHeight) {
                                SavedPostDetailScreen(
                                    onNavigateUp = { navController.navigateUp() },
                                )
                            }
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

                if (showBottomBar) {
                    AppBottomBarOverlay(
                        isCollectionRoute = isCollectionRoute,
                        collectionBarsVisible = bottomBarBinding.barsVisible,
                        navItems = navItems,
                        selectedTab = selectedTab,
                        onNavItemClick = onBottomBarItemClick,
                        onHeightChanged = { height ->
                            // Retain the complete bar height after a collection scroll hides it,
                            // keeping both timeline and ordinary-tab viewports stable.
                            bottomOverlayHeightPx = height
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
