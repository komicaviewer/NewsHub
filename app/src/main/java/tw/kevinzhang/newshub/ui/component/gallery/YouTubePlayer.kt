package tw.kevinzhang.newshub.ui.component.gallery

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancesco.soffritti.android.youtubeplayer.core.player.YouTubePlayer
import com.pierfrancesco.soffritti.android.youtubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancesco.soffritti.android.youtubeplayer.core.ui.YouTubePlayerView

@Composable
fun YouTubePlayer(videoId: String) {
    AndroidView(
        factory = { ctx ->
            YouTubePlayerView(ctx).apply {
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoId, 0f)
                    }
                })
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    )
}

fun extractYouTubeVideoId(watchUrl: String): String? =
    Regex("""[?&]v=([A-Za-z0-9_-]+)""").find(watchUrl)?.groupValues?.get(1)
