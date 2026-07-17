package tw.kevinzhang.newshub.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.ThreadSummary

class ThreadSummaryCardTest {
    @Test
    fun `card content keeps unique preview videos in order`() {
        val firstVideo = Paragraph.VideoInfo("https://example.com/first.mp4")
        val duplicateVideo = Paragraph.VideoInfo("https://example.com/first.mp4")
        val secondVideo = Paragraph.VideoInfo("https://example.com/second.mp4")
        val text = Paragraph.Text("content")

        val content = summary(
            previewContent = listOf(firstVideo, text, duplicateVideo, secondVideo),
        ).cardContent(alwaysUseRawImage = false)

        assertEquals(listOf(firstVideo, text, secondVideo), content.previewContent)
    }

    @Test
    fun `card content uses configured summary image when distinct from videos`() {
        val summary = summary(
            thumbnail = "https://example.com/thumb.jpg",
            rawImage = "https://example.com/raw.jpg",
            previewContent = listOf(Paragraph.VideoInfo("https://example.com/video.mp4")),
        )

        assertEquals(
            "https://example.com/thumb.jpg",
            summary.cardContent(alwaysUseRawImage = false).imageUrl,
        )
        assertEquals(
            "https://example.com/raw.jpg",
            summary.cardContent(alwaysUseRawImage = true).imageUrl,
        )
    }

    @Test
    fun `card content suppresses image when its URL is already rendered as video`() {
        val mediaUrl = "https://example.com/media.mp4"
        val summary = summary(
            thumbnail = mediaUrl,
            previewContent = listOf(Paragraph.VideoInfo(mediaUrl)),
        )

        assertNull(summary.cardContent(alwaysUseRawImage = false).imageUrl)
    }

    private fun summary(
        thumbnail: String? = null,
        rawImage: String? = null,
        previewContent: List<Paragraph> = emptyList(),
    ) = ThreadSummary(
        sourceId = "source",
        boardUrl = "https://example.com/board",
        id = "thread",
        title = null,
        author = null,
        createdAt = null,
        commentCount = null,
        rawImage = rawImage,
        thumbnail = thumbnail,
        previewContent = previewContent,
    )
}
