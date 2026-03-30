package tw.kevinzhang.newshub.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Comment
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenWebClick: (url: String) -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel(),
) {
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val threadUrl by viewModel.threadUrl.collectAsStateWithLifecycle()
    val previewPost by viewModel.previewPost.collectAsStateWithLifecycle()
    val commentStates by viewModel.commentStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(thread?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (threadUrl != null) {
                        IconButton(onClick = { onOpenWebClick(threadUrl!!) }) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in browser",
                            )
                        }
                    }
                }
            )
        },
    ) { padding ->
        if (thread == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            val onReplyToClick =
                remember(viewModel) { { id: String -> viewModel.onReplyToClick(id) } }
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(thread!!.posts, key = { it.id }) { post ->
                    ExtPostCard(
                        post = post,
                        commentUiState = commentStates[post.id],
                        onReplyToClick = onReplyToClick,
                        onLoadMoreCommentsClick = { viewModel.loadMoreComments(post.id) },
                    )
                }
            }
        }
    }

    previewPost?.let { post ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPreview() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissPreview() }) { Text("Close") }
            },
            title = { Text("Post ${post.id}") },
            text = {
                Column {
                    post.content.forEach { paragraph ->
                        when (paragraph) {
                            is Paragraph.Text -> Text(paragraph.content)
                            is Paragraph.Quote -> Text("> ${paragraph.content}")
                            is Paragraph.ReplyTo -> Text(">> ${paragraph.id}")
                            is Paragraph.Link -> Text(paragraph.content)
                            is Paragraph.ImageInfo -> paragraph.thumb?.let { url ->
                                AsyncImage(model = url, contentDescription = null)
                            }

                            is Paragraph.VideoInfo -> Text("[video]")
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ExtPostCard(
    post: Post,
    commentUiState: CommentUiState?,
    onReplyToClick: (String) -> Unit,
    onLoadMoreCommentsClick: () -> Unit,
) {
    AppCard {
        Column(modifier = Modifier.padding(dimensionResource(R.dimen.space_8))) {
            Text(text = "Post ${post.id}", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_4)))
            post.content.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> Text(paragraph.content)
                    is Paragraph.Quote -> Text(
                        "> ${paragraph.content}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    is Paragraph.ReplyTo -> TextButton(
                        onClick = { onReplyToClick(paragraph.id) },
                        contentPadding = PaddingValues(0.dp),
                    ) { Text(">> ${paragraph.id}") }

                    is Paragraph.Link -> Text(
                        paragraph.content,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is Paragraph.ImageInfo -> paragraph.thumb?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is Paragraph.VideoInfo -> Text("[video: ${paragraph.url}]")
                }
            }
            val visibleComments = commentUiState?.visibleComments.orEmpty()
            if (visibleComments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_8)))
                visibleComments.forEach { comment ->
                    CommentItem(comment = comment)
                }
            }
            when {
                commentUiState?.isLoading == true ->
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = dimensionResource(R.dimen.space_4)),
                        strokeWidth = 2.dp,
                    )
                commentUiState?.hasMore == true ->
                    TextButton(
                        onClick = onLoadMoreCommentsClick,
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("載入更多留言", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_8)))
}

@Composable
private fun CommentItem(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.space_8), vertical = dimensionResource(R.dimen.space_4)),
        verticalAlignment = Alignment.Top,
    ) {
        // 頭像佔位
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.space_8)))
        Column(modifier = Modifier.weight(1f)) {
            comment.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            comment.content.forEach { paragraph ->
                when (paragraph) {
                    is Paragraph.Text -> Text(paragraph.content, style = MaterialTheme.typography.bodySmall)
                    is Paragraph.Quote -> Text("> ${paragraph.content}", style = MaterialTheme.typography.bodySmall)
                    is Paragraph.ReplyTo -> Text(">> ${paragraph.id}", style = MaterialTheme.typography.bodySmall)
                    is Paragraph.Link -> Text(paragraph.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    is Paragraph.ImageInfo -> paragraph.thumb?.let { url ->
                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth())
                    }
                    is Paragraph.VideoInfo -> Text("[video]", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
