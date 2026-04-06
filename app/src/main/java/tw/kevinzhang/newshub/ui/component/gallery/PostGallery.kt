package tw.kevinzhang.newshub.ui.component.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.newshub.ui.component.Small
import tw.kevinzhang.newshub.ui.component.View

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostGallery(
    paragraphs: List<Paragraph>,
    startIndex: Int = 0,
    onDismissRequest: () -> Unit,
    onReplyToClick: ((String) -> Unit)? = null,
) {
    val mediaItems = remember(paragraphs) {
        paragraphs.filter { it is Paragraph.ImageInfo || it is Paragraph.VideoInfo }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // ── 上方媒體畫廊 ──────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                val pagerState = rememberPagerState(initialPage = startIndex) { mediaItems.size }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (val item = mediaItems[page]) {
                        is Paragraph.ImageInfo -> {
                            var loaded by remember { mutableStateOf(false) }
                            ZoomableBox(loaded = loaded) {
                                AsyncImage(
                                    model = item.raw,
                                    contentDescription = null,
                                    onSuccess = { loaded = true },
                                )
                            }
                        }
                        is Paragraph.VideoInfo -> VideoPlayer(url = item.url)
                        else -> Unit
                    }
                }

                // 頁碼
                if (mediaItems.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .systemBarsPadding()
                            .padding(end = 56.dp, top = 4.dp),
                    )
                }

                // 關閉按鈕
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .systemBarsPadding(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }

            // ── 下方文章內容 ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                paragraphs.forEach { paragraph ->
                    when (paragraph) {
                        is Paragraph.Text -> paragraph.View()
                        is Paragraph.Quote -> paragraph.Small()
                        is Paragraph.ReplyTo -> paragraph.View()
                        is Paragraph.Link -> paragraph.View()
                        is Paragraph.ImageInfo, is Paragraph.VideoInfo -> Unit
                    }
                }
            }
        }
    }
}
