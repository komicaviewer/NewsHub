package tw.kevinzhang.newshub.ui.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import tw.kevinzhang.newshub.R
import tw.kevinzhang.newshub.ui.component.AppCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: ThreadDetailViewModel = hiltViewModel(),
) {
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val previewPost by viewModel.previewPost.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(thread?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
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
            val onReplyToClick = remember(viewModel) { { id: String -> viewModel.onReplyToClick(id) } }
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(thread!!.posts, key = { it.id }) { post ->
                    ExtPostCard(
                        post = post,
                        onReplyToClick = onReplyToClick,
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
    onReplyToClick: (String) -> Unit,
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
        }
    }
    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.space_8)))
}
