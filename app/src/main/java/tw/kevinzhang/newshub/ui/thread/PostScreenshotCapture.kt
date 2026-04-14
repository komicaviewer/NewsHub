package tw.kevinzhang.newshub.ui.thread

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post
import java.io.File
import kotlin.coroutines.resume

/**
 * Renders each post off-screen via a ComposeView with software rendering
 * (LAYER_TYPE_SOFTWARE), captures via view.draw(Canvas), saves to
 * filesDir/saved_posts/{sourceId}_{threadId}/post_{i}.png, and returns
 * the list of absolute file paths.
 *
 * Software rendering (LAYER_TYPE_SOFTWARE) is used so that view.draw()
 * writes to a CPU-backed Canvas regardless of whether the view is visible
 * on screen or covered by other views — bypassing the GPU clip optimisation
 * that would otherwise skip a fully-occluded hardware layer.
 */
suspend fun capturePostsAsFiles(
    activity: Activity,
    posts: List<Post>,
    alwaysUseRawImage: Boolean,
    sourceId: String,
    threadId: String,
): List<String> {
    val dir = File(activity.filesDir, "saved_posts/${sourceId}_${threadId}").also { it.mkdirs() }

    // One shared software ImageLoader for the entire capture session.
    // Coil's default is HARDWARE bitmaps; software rendering cannot draw them.
    val softwareImageLoader = ImageLoader.Builder(activity)
        .allowHardware(false)
        .build()

    try {
        return posts.mapIndexed { index, post ->
            // Pre-load all images for this post before capturing.
            // This ensures AsyncImage finds them in Coil's memory cache immediately.
            preLoadImages(activity, softwareImageLoader, post, alwaysUseRawImage)

            val bitmap = capturePostAsBitmap(activity, alwaysUseRawImage, post, softwareImageLoader)
            val file = File(dir, "post_$index.png")
            withContext(Dispatchers.IO) {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            file.absolutePath
        }
    } finally {
        softwareImageLoader.shutdown()
    }
}

private suspend fun preLoadImages(
    activity: Activity,
    loader: ImageLoader,
    post: Post,
    alwaysUseRawImage: Boolean
) {
    val urls = mutableListOf<String>()
    post.sourceIconUrl?.let { urls.add(it) }
    post.content.forEach { p ->
        when (p) {
            is Paragraph.ImageInfo -> urls.add(if (alwaysUseRawImage) p.raw else p.thumb ?: p.raw)
            is Paragraph.VideoInfo -> urls.add(p.url)
            else -> {}
        }
    }

    if (urls.isEmpty()) return

    withContext(Dispatchers.IO) {
        val requests = urls.distinct().map { url ->
            async {
                val request = ImageRequest.Builder(activity)
                    .data(url)
                    .build()
                loader.execute(request)
            }
        }
        requests.awaitAll()
    }
}

private suspend fun capturePostAsBitmap(
    activity: Activity,
    alwaysUseRawImage: Boolean,
    post: Post,
    softwareImageLoader: ImageLoader,
): Bitmap = withContext(Dispatchers.Main) {
    val decorView = activity.window.decorView as FrameLayout
    val displayWidth = activity.resources.displayMetrics.widthPixels

    val composeView = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        // Software rendering: view.draw(canvas) works on any CPU canvas,
        // independent of GPU pipeline and screen visibility.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        setContent {
            // Explicit white background inside Compose so the background is
            // captured by draw(), rather than relying on the Android View background.
            Box(modifier = Modifier.background(Color.White)) {
                OffscreenPostCard(
                    post = post,
                    alwaysUseRawImage = alwaysUseRawImage,
                    imageLoader = softwareImageLoader,
                )
            }
        }
    }

    // Add at index 0 (below all existing views) so it is within window bounds
    // and participates in the Compose frame clock.
    val lp = FrameLayout.LayoutParams(displayWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
    decorView.addView(composeView, 0, lp)

    return@withContext try {
        // Wait until the Compose content has been laid out (height > 0).
        suspendCancellableCoroutine<Unit> { cont ->
            composeView.viewTreeObserver.addOnGlobalLayoutListener(
                object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (composeView.height > 0) {
                            composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            // Post one frame to ensure at least one Compose draw pass has run.
                            composeView.post {
                                if (cont.isActive) {
                                    // A small additional delay helps ensure that any sub-compositions
                                    // or layout adjustments triggered by images are settled.
                                    composeView.post { cont.resume(Unit) }
                                }
                            }
                        }
                    }
                }
            )
        }

        // Final safety wait for all side-effects and layout shifts to finish
        delay(150)

        // Draw the software-rendered Compose view onto a bitmap-backed Canvas.
        val bitmap = Bitmap.createBitmap(composeView.width, composeView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        composeView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } finally {
        try { decorView.removeView(composeView) } catch (_: Exception) {}
    }
}

/**
 * A lightweight wrapper that renders only post content — suitable for off-screen capture.
 *
 * Provides a software-only ImageLoader so Coil never produces hardware bitmaps,
 * which would crash when drawn onto the software Canvas used for capture.
 */
@Composable
internal fun OffscreenPostCard(post: Post, alwaysUseRawImage: Boolean, imageLoader: ImageLoader) {
    CompositionLocalProvider(LocalImageLoader provides imageLoader) {
        PostCard(
            post = post,
            highlightAlpha = 0f,
            useWebView = false,
            onEnableWebView = {},
            alwaysUseRawImage = alwaysUseRawImage,
            onShowReplies = {},
            onReplyToClick = {},
            onMediaClick = {},
            textZoom = 100,
            onZoomChange = {},
        )
    }
}
