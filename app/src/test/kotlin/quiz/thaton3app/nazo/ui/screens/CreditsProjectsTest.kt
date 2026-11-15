package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsProjectsTest {

    @Test
    fun leadDeveloperHighlightsUseTheRequestedCurrentProjects() {
        assertEquals(
            listOf("TwinPane", "Shouze", "ShakeIT", "ghapk"),
            LEAD_DEVELOPER_PROJECTS.map { it.title },
        )
        assertTrue(LEAD_DEVELOPER_PROJECTS.all { it.detail.isNotBlank() })
    }

    @Test
    fun archivedAndExcludedProjectsAreNotListed() {
        val titles = LEAD_DEVELOPER_PROJECTS.map { it.title }.toSet()
        assertFalse("ProjectR" in titles)
        assertFalse("UtilityKit" in titles)
        assertFalse("MindRelay" in titles)
    }
}
