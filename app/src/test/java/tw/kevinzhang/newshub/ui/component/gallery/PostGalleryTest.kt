package tw.kevinzhang.newshub.ui.component.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph

class PostGalleryTest {

    @Test
    fun galleryMediaItems_preservesMixedImageAndVideoOrder() {
        val imageOne = Paragraph.ImageInfo(raw = "https://example.com/1.jpg")
        val video = Paragraph.VideoInfo(url = "https://example.com/video.mp4")
        val imageTwo = Paragraph.ImageInfo(raw = "https://example.com/2.jpg")

        val result = galleryMediaItems(
            listOf(
                Paragraph.Text("before"),
                imageOne,
                Paragraph.Quote("between"),
                video,
                imageTwo,
            ),
        )

        assertEquals(listOf(imageOne, video, imageTwo), result)
    }

    @Test
    fun galleryMediaItems_returnsEmptyListForTextOnlyPost() {
        val result = galleryMediaItems(
            listOf(
                Paragraph.Text("text"),
                Paragraph.ReplyTo(targetId = "123"),
                Paragraph.Link("https://example.com"),
            ),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun galleryInitialPage_clampsToAvailableMedia() {
        assertEquals(0, galleryInitialPage(startIndex = -2, itemCount = 3))
        assertEquals(1, galleryInitialPage(startIndex = 1, itemCount = 3))
        assertEquals(2, galleryInitialPage(startIndex = 8, itemCount = 3))
        assertEquals(0, galleryInitialPage(startIndex = 2, itemCount = 0))
    }

    @Test
    fun galleryPanelState_mediaTapMovesBetweenImmersiveAndExpanded() {
        assertEquals(
            GalleryPanelState.Expanded,
            GalleryPanelState.Immersive.onMediaTap(),
        )
        assertEquals(
            GalleryPanelState.Immersive,
            GalleryPanelState.Expanded.onMediaTap(),
        )
    }

    @Test
    fun galleryPanelVisibleFraction_tracksDragOffset() {
        assertEquals(
            1f,
            galleryPanelVisibleFraction(
                offset = 0f,
                dragRange = 400f,
                fallbackState = GalleryPanelState.Immersive,
            ),
        )
        assertEquals(
            0.5f,
            galleryPanelVisibleFraction(
                offset = 200f,
                dragRange = 400f,
                fallbackState = GalleryPanelState.Immersive,
            ),
        )
        assertEquals(
            0f,
            galleryPanelVisibleFraction(
                offset = 400f,
                dragRange = 400f,
                fallbackState = GalleryPanelState.Expanded,
            ),
        )
    }

    @Test
    fun galleryPanelVisibleFraction_clampsOffsetToAnchors() {
        assertEquals(
            1f,
            galleryPanelVisibleFraction(
                offset = -100f,
                dragRange = 400f,
                fallbackState = GalleryPanelState.Immersive,
            ),
        )
        assertEquals(
            0f,
            galleryPanelVisibleFraction(
                offset = 500f,
                dragRange = 400f,
                fallbackState = GalleryPanelState.Expanded,
            ),
        )
    }

    @Test
    fun galleryPanelVisibleFraction_usesFallbackUntilAnchorsAreReady() {
        assertEquals(
            1f,
            galleryPanelVisibleFraction(
                offset = Float.NaN,
                dragRange = 0f,
                fallbackState = GalleryPanelState.Expanded,
            ),
        )
        assertEquals(
            0f,
            galleryPanelVisibleFraction(
                offset = Float.NaN,
                dragRange = Float.NaN,
                fallbackState = GalleryPanelState.Immersive,
            ),
        )
    }
}
