package tw.kevinzhang.komica_api.model;

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KPostTest {

    @Test
    fun `Test replyTo extension expect successful`() {
        val post1 = KPostBuilder().setContent(
            listOf(
                KReplyTo("A"),
                KReplyTo("B"),
                KReplyTo("C"),
            )
        ).build()

        assertEquals(3, post1.replyTo().size)
    }

    @Test
    fun `Test filterReplyToIs extension expect successful`() {
        val post1 = KPostBuilder().setContent(
            listOf(
                KReplyTo("A"),
                KReplyTo("B"),
                KReplyTo("C"),
            )
        ).build()

        val post2 = KPostBuilder().setContent(
            listOf(
                KReplyTo("A"),
                KReplyTo("B"),
            )
        ).build()

        val post3 = KPostBuilder().setContent(
            listOf(
                KReplyTo("A"),
            )
        ).build()

        assertEquals(2, listOf(post1, post2, post3).filterReplyToIs("B").size)
    }
}
