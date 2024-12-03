package tw.kevinzhang.newshub.ui.topic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.paging.compose.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.fragula2.compose.FragulaNavHost
import com.fragula2.compose.swipeable
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshState
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.flow.flowOf
import tw.kevinzhang.hub_server.data.Paragraph
import tw.kevinzhang.hub_server.data.Host
import tw.kevinzhang.hub_server.data.board.Board
import tw.kevinzhang.hub_server.data.news.News
import tw.kevinzhang.hub_server.data.news.gamer.GamerNews
import tw.kevinzhang.hub_server.data.news.komica.KomicaNews
import tw.kevinzhang.hub_server.data.news.komica.mockKomicaNews
import tw.kevinzhang.hub_server.data.post.komica.KomicaPost
import tw.kevinzhang.hub_server.data.topic.Topic
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.encode
import tw.kevinzhang.newshub.ui.comment.CommentListRoute
import tw.kevinzhang.newshub.ui.comment.CommentListViewModel
import tw.kevinzhang.newshub.ui.component.*
import tw.kevinzhang.newshub.ui.navigation.bottomNavItems
import tw.kevinzhang.newshub.ui.news.GamerNewsCard
import tw.kevinzhang.newshub.ui.news.KomicaNewsCard
import tw.kevinzhang.newshub.ui.news.KomicaPostCard
import tw.kevinzhang.newshub.ui.theme.NewshubTheme
import tw.kevinzhang.newshub.ui.thread.ThreadRoute
import tw.kevinzhang.newshub.ui.thread.ThreadViewModel


@Composable
fun TopicRoute(
    openDrawer: () -> Unit,
    topicId: String,
) {
    val navController = rememberNavController()
    FragulaNavHost(
        navController = navController,
        startDestination = "list",
    ) {
        swipeable("list") {
            val factory = HiltViewModelFactory(LocalContext.current, it)
            val newsListViewModel = viewModel<NewsListViewModel>(factory = factory)
            newsListViewModel.topicId(topicId)
            NewsListRoute(
                newsListViewModel = newsListViewModel,
                navController = navController,
                openDrawer = { openDrawer() },
            )
        }

        swipeable("thread/{threadUrl}") {
            val factory = HiltViewModelFactory(LocalContext.current, it)
            val threadViewModel = viewModel<ThreadViewModel>(factory = factory)
            it.arguments?.getString("threadUrl")?.let { threadUrl ->
                threadViewModel.thread(threadUrl)
            }
            ThreadRoute(
                threadViewModel = threadViewModel,
                navController = navController,
            )
        }

        swipeable("thread/{threadUrl}/re-post/{rePostId}") {
            val factory = HiltViewModelFactory(LocalContext.current, it)
            val threadViewModel = viewModel<ThreadViewModel>(factory = factory)
            it.arguments?.getString("threadUrl")?.let { threadUrl ->
                threadViewModel.thread(threadUrl)
            }
            it.arguments?.getString("rePostId")?.let { rePostId ->
                threadViewModel.rePostId(rePostId)
            }
            ThreadRoute(
                threadViewModel = threadViewModel,
                navController = navController,
            )
        }

        swipeable("comments/{commentsUrl}") {
            val factory = HiltViewModelFactory(LocalContext.current, it)
            val commentListViewModel = viewModel<CommentListViewModel>(factory = factory)
            it.arguments?.getString("commentsUrl")?.let { commentsUrl ->
                commentListViewModel.thread(commentsUrl)
            }
            CommentListRoute(
                commentListViewModel = commentListViewModel,
                navController = navController,
            )
        }
    }
}

@Composable
fun NewsListRoute(
    newsListViewModel: NewsListViewModel,
    navController: NavHostController,
    openDrawer: () -> Unit,
){
    val topic by newsListViewModel.topic.collectAsState(null)
    val pagingNews = newsListViewModel.pagingNews.collectAsLazyPagingItems()
    val allBoards by newsListViewModel.allBoards.collectAsState(emptyList())
    val subscribedBoards by newsListViewModel.subscribedBoards.collectAsState(emptyList())
    val selectedBoards by newsListViewModel.selectedBoards.collectAsState(emptyList())
    var loading by remember { mutableStateOf(false) }
    val refreshState = rememberSwipeRefreshState(loading)
    var showBoardPicker by remember { mutableStateOf(false) }
    val navigateToNews = { news: News -> navController.navigate("thread/${news.threadUrl.encode()}") }
    val context = LocalContext.current

    fun onLinkClick(link: Paragraph.Link) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.content))
        ContextCompat.startActivity(context, intent, null)
    }

    fun onImageClick(image: Paragraph.ImageInfo) {
    }

    TopicScreen(
        refreshState = refreshState,
        lazyColumnState = pagingNews.rememberLazyListState(),
        topic = topic,
        pagingNews = pagingNews,
        subscribedBoards = subscribedBoards,
        selectedBoards = selectedBoards,
        openDrawer = openDrawer,
        onRefresh = {
            newsListViewModel.clearAllNews()
            pagingNews.refresh()
        },
        onActiveBoardClick = {
            newsListViewModel.unselectBoard(it)
        },
        onInactiveBoardClick = {
            newsListViewModel.selectBoard(it)
        },
        onAddBoardClick = {
            showBoardPicker = true
        },
        onLoadStateChange = { loadState ->
            loadState.apply {
                when {
                    refresh is LoadState.Loading -> {
                        loading = true
                    }
                    refresh is LoadState.NotLoading -> {
                        loading = false
                    }
                    refresh is LoadState.Error -> {
                        loading = false
                    }
                    prepend is LoadState.Loading -> {
                        loading = true
                    }
                    prepend is LoadState.NotLoading -> {
                        loading = false
                    }
                    prepend is LoadState.Error -> {
                        loading = false
                    }
                }
            }
        },
        news = { news ->
            var boardName by remember { mutableStateOf("") }
            LaunchedEffect(news) {
                news?.let { boardName = newsListViewModel.readBoardName(it.boardUrl) }
            }
            when (news) {
                is KomicaNews -> KomicaNewsCard(
                    news = news,
                    boardName = boardName,
                    onParagraphClick = {
                        when (it) {
                            is Paragraph.Link -> onLinkClick(it)
                            is Paragraph.ImageInfo -> onImageClick(it)
                            else -> { }
                        }
                    },
                    onClick = { navigateToNews(news) },
                )
                is GamerNews -> GamerNewsCard(
                    news = news,
                    boardName = boardName,
                    onParagraphClick = {
                        when (it) {
                        is Paragraph.Link -> onLinkClick(it)
                        else -> { }
                        }
                    },
                    onClick = { navigateToNews(news) },
                )
                else -> Text(text = "not support")
            }
        },
    )

    if (showBoardPicker) {
        AddBoardDialog(
            onDismissRequest = { showBoardPicker = false },
            allBoards = allBoards,
            checked = subscribedBoards,
            onBoardClick = {
                if (subscribedBoards.contains(it)) {
                    newsListViewModel.unsubscribeBoard(it)
                } else {
                    newsListViewModel.subscribeBoard(it)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topic: Topic? = null,
    refreshState: SwipeRefreshState,
    lazyColumnState: LazyListState,
    pagingNews: LazyPagingItems<News>,
    subscribedBoards: List<Board>,
    selectedBoards: List<Board>,
    onActiveBoardClick: (Board) -> Unit,
    onInactiveBoardClick: (Board) -> Unit,
    onAddBoardClick: () -> Unit,
    openDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onLoadStateChange: (CombinedLoadStates) -> Unit = { },
    news: @Composable (News?) -> Unit,
) {
    Scaffold(
        topBar = {
            NewsHubTopBar(
                onMenuPressed = { openDrawer() },
                title = topic?.name ?: ""
            )
        },
        bottomBar = { AppBottomBar(bottomNavItems()) },
    ) {
        Surface(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
        ) {
            SwipeRefresh(
                state = refreshState,
                onRefresh = onRefresh,
            ) {
                LazyColumn(
                    state = lazyColumnState,
                ) {
                    item {
                        BoardFilter(
                            boards = subscribedBoards,
                            selected = selectedBoards,
                            onActiveClick = { onActiveBoardClick(it) },
                            onInactiveClick = { onInactiveBoardClick(it) },
                            onAddBoardClick = onAddBoardClick,
                        )
                    }
                    pagingNews.apply {
                        item {
                            onLoadStateChange(loadState)
                        }
                        items(this) { news ->
                            news(news)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewTopicScreen() {
    val mockNewsfeed = flowOf(PagingData.from(listOf<News>(mockKomicaNews()))).collectAsLazyPagingItems()

    NewshubTheme {
        TopicScreen(
            topic = Topic("Square", "Square", 0),
            refreshState = rememberSwipeRefreshState(isRefreshing = false),
            pagingNews = mockNewsfeed,
            lazyColumnState = mockNewsfeed.rememberLazyListState(),
            subscribedBoards = emptyList(),
            selectedBoards = emptyList(),
            openDrawer = { },
            onRefresh = { },
            onActiveBoardClick = { },
            onInactiveBoardClick = { },
            onAddBoardClick = { },
            news = { news ->
                KomicaPostCard(
                    post = news as KomicaPost,
                    boardName = "Board",
                    onParagraphClick = { },
                    onClick = { }
                )
            },
        )
    }
}

@Composable
fun AddBoardDialog(
    onDismissRequest: () -> Unit,
    allBoards: List<Board>,
    checked: List<Board>,
    onBoardClick: (Board) -> Unit,
) {
    AppDialog(onDismissRequest = onDismissRequest) {
        AppList(list = allBoards) { item ->
            AppMaxWidthItem(
                title = item.name,
                onClick = { onBoardClick(item) },
                icon = {
                    if (checked.contains(item)) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "check",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        BlankIcon()
                    }
                },
            )
        }
    }
}

@Composable
fun BoardFilter(
    boards: List<Board> = emptyList(),
    selected: List<Board> = emptyList(),
    onActiveClick: (Board) -> Unit = { },
    onInactiveClick: (Board) -> Unit = { },
    onAddBoardClick: () -> Unit = { },
) {
    val mockBoard = Board("", "", Host.KOMICA)
    val icons = boards.plus(mockBoard)
    Column(
        modifier = Modifier
            .padding(dimensionResource(id = R.dimen.space_16)),
    ) {
        icons.chunked(4).forEach { boards ->
            Row {
                boards.forEach {
                    val active = selected.contains(it)
                    if (it == mockBoard) {
                        AddBoardIcon(onClick = onAddBoardClick)
                    } else {
                        BoardIcon(it, active) {
                            if (active) {
                                onActiveClick(it)
                            } else {
                                onInactiveClick(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewBoardFilter() {
    val boards = listOf(
        Board(name = "A", url = "a.com", host = Host.KOMICA),
        Board(name = "B", url = "b.com", host = Host.GAMER),
        Board(name = "C", url = "c.com", host = Host.GAMER),
        Board(name = "D", url = "d.com", host = Host.GAMER),
        Board(name = "E", url = "e.com", host = Host.GAMER),
    )
    NewshubTheme {
        BoardFilter(
            boards = boards,
            selected = boards.subList(1, 2),
        )
    }
}

@Composable
fun BoardIcon(
    board: Board,
    selected: Boolean = false,
    onClick: () -> Unit = { },
) {
    NavigationRailItem(
        label = { Text(board.name) },
        icon = { Icon(painterResource(R.drawable.ic_outline_globe_24), contentDescription = "") },
        selected = selected,
        onClick = onClick,
    )
}

@Composable
fun AddBoardIcon(
    onClick: () -> Unit = { },
) {
    NavigationRailItem(
        label = { Text(stringResource(id = R.string.add_board)) },
        icon = { Icon(painterResource(R.drawable.ic_outline_add_24), contentDescription = "") },
        selected = false,
        onClick = onClick,
    )
}

@Preview
@Composable
fun PreviewBoardIcon() {
    NewshubTheme {
        BoardIcon(Board(name = "A", url = "a.com", host = Host.KOMICA))
    }
}

@Preview
@Composable
fun PreviewAddBoardIcon() {
    NewshubTheme {
        AddBoardIcon()
    }
}

@Composable
fun LoadingFooter() {
    Text(text = "Loading", fontSize = 26.sp)
}

@Composable
fun Footer() {
    Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.space_65)))
}

@Composable
fun Error(error: LoadState.Error) {
    Text(error.error.stackTraceToString())
}

/**
 * Paging lib has a issue about "Scroll position of LazyColumn built with `collectAsLazyPagingItems` is lost when using Navigation."
 * issue link: https://issuetracker.google.com/issues/177245496#comment24
 */
@Composable
fun <T : Any> LazyPagingItems<T>.rememberLazyListState(): LazyListState {
    // After recreation, LazyPagingItems first return 0 items, then the cached items.
    // This behavior/issue is resetting the LazyListState scroll position.
    // Below is a workaround. More info: https://issuetracker.google.com/issues/177245496.
    return when (itemCount) {
        // Return a different LazyListState instance.
        0 -> remember(this) { LazyListState(0, 0) }
        // Return rememberLazyListState (normal case).
        else -> androidx.compose.foundation.lazy.rememberLazyListState()
    }
}