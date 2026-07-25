package tw.kevinzhang.newshub.ui.component.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.extension_api.model.Paragraph
import tw.kevinzhang.extension_api.model.Post

@RunWith(AndroidJUnit4::class)
class PostGalleryInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rightBlankEdge_scrollsContent_withoutDraggingPanel() {
        composeRule.setContent {
            MaterialTheme {
                PostGallery(
                    post = longPost(),
                    onDismissRequest = {},
                )
            }
        }

        val scrollContent = composeRule.onNodeWithTag(GalleryPanelScrollContentTag)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            scrollContent.scrollRange().maxValue() > 0f
        }
        val initialScroll = scrollContent.scrollRange().value()

        scrollContent.performTouchInput {
            swipe(
                start = Offset(right - 2f, height * 0.8f),
                end = Offset(right - 2f, height * 0.2f),
                durationMillis = 500,
            )
        }
        composeRule.waitForIdle()

        assertTrue(
            "Swiping from the content's right edge should scroll the post",
            scrollContent.scrollRange().value() > initialScroll,
        )
        composeRule
            .onNodeWithContentDescription("上下拖曳貼文資訊")
            .assert(hasStateDescription("已展開"))

        repeat(3) {
            scrollContent.performTouchInput {
                swipe(
                    start = Offset(right - 2f, height * 0.2f),
                    end = Offset(right - 2f, height * 0.8f),
                    durationMillis = 300,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription("上下拖曳貼文資訊")
            .assert(hasStateDescription("已展開"))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollRange() =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun longPost() = Post(
        id = "gallery-scroll-test",
        author = "tester",
        createdAt = null,
        thumbnail = null,
        content = buildList {
            add(Paragraph.ImageInfo(raw = "https://example.invalid/test.jpg"))
            repeat(40) { index ->
                add(Paragraph.Text("Paragraph $index with enough content to require scrolling."))
            }
        },
        comments = emptyList(),
    )
}
