package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import quiz.thaton3app.nazo.data.ChangelogEntry

class WhatsNewTest {
    @Test
    fun `newest pre-release drives the sheet gate and content`() {
        val beta = ChangelogEntry(
            version = "14.0-beta01",
            date = "12 Nov 2026",
            changes = listOf("Try the opt-in pre-release channel."),
        )
        val stable = ChangelogEntry(
            version = "13.0",
            date = "11 Nov 2026",
            changes = listOf("Previous stable notes."),
        )

        val content = latestWhatsNewContent(listOf(beta, stable))

        assertEquals("release-14.0-beta01", content.id)
        assertEquals("Nazo 14.0-beta01", content.title)
        assertEquals(beta.changes, content.points)
    }

    @Test
    fun `empty changelog is rejected instead of silently suppressing the sheet`() {
        assertThrows(IllegalArgumentException::class.java) {
            latestWhatsNewContent(emptyList())
        }
    }
}
