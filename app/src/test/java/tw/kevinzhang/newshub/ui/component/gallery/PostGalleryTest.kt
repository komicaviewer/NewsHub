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
    fun galleryPanelState_mediaTapMovesBetweenImmersiveAndSummary() {
        assertEquals(
            GalleryPanelState.Summary,
            GalleryPanelState.Immersive.onMediaTap(),
        )
        assertEquals(
            GalleryPanelState.Immersive,
            GalleryPanelState.Summary.onMediaTap(),
        )
        assertEquals(
            GalleryPanelState.Immersive,
            GalleryPanelState.Expanded.onMediaTap(),
        )
    }

    @Test
    fun galleryPanelState_handleDragRequiresThreshold() {
        assertEquals(
            GalleryPanelState.Summary,
            GalleryPanelState.Summary.onHandleDrag(dragAmount = -47f),
        )
        assertEquals(
            GalleryPanelState.Expanded,
            GalleryPanelState.Summary.onHandleDrag(dragAmount = -48f),
        )
        assertEquals(
            GalleryPanelState.Summary,
            GalleryPanelState.Expanded.onHandleDrag(dragAmount = 48f),
        )
        assertEquals(
            GalleryPanelState.Immersive,
            GalleryPanelState.Summary.onHandleDrag(dragAmount = 48f),
        )
        assertEquals(
            GalleryPanelState.Expanded,
            GalleryPanelState.Expanded.onHandleDrag(dragAmount = -80f),
        )
    }

    @Test
    fun gallerySummaryText_usesOnlyHumanReadableParagraphContent() {
        val summary = gallerySummaryText(
            listOf(
                Paragraph.ImageInfo(raw = "https://example.com/1.jpg"),
                Paragraph.Text("  第一段  "),
                Paragraph.RichText(runs = listOf(tw.kevinzhang.extension_api.model.RichTextRun("第二段"))),
                Paragraph.ReplyTo(targetId = "123", preview = "回覆摘要"),
                Paragraph.ReplyTo(targetId = "456"),
                Paragraph.VideoInfo(url = "https://example.com/video.mp4"),
            ),
        )

        assertEquals("第一段\n第二段\n回覆摘要\n回覆 #456", summary)
    }
}
