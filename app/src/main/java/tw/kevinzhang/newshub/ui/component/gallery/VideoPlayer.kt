package tw.kevinzhang.newshub.ui.component.gallery

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import tw.kevinzhang.extension_api.ResourceHandle
import tw.kevinzhang.newshub.auth.ResourceHandleDataSource
import tw.kevinzhang.newshub.auth.hostResourceProvider

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(handleModel: String, modifier: Modifier = Modifier) {
    val handle = remember(handleModel) { ResourceHandle.parse(handleModel) } ?: return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resourceProvider = remember(context) { context.hostResourceProvider() }
    val exoPlayer = remember(handle.asModel(), context, resourceProvider) {
        ExoPlayer.Builder(context).build().apply {
            val mediaSource = ProgressiveMediaSource.Factory(
                ResourceHandleDataSource.Factory(resourceProvider),
            ).createMediaSource(MediaItem.fromUri(handle.asModel()))
            setMediaSource(mediaSource)
            prepare()
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        var resumeAfterForeground = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterForeground = exoPlayer.playWhenReady
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeAfterForeground) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                player = exoPlayer
            }
        },
        update = { it.player = exoPlayer },
    )
}
