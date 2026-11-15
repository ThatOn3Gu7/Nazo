package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePictureViewerSheetTest {

    @Test
    fun portraitViewerUsesALargeButBoundedPreview() {
        assertEquals(
            259,
            profilePictureViewerSizeDp(screenWidthDp = 360, screenHeightDp = 800),
        )
    }

    @Test
    fun landscapeViewerRespectsTheShorterWindowEdge() {
        val portrait = profilePictureViewerSizeDp(screenWidthDp = 360, screenHeightDp = 800)
        val landscape = profilePictureViewerSizeDp(screenWidthDp = 800, screenHeightDp = 360)

        assertEquals(208, landscape)
        assertTrue(landscape < portrait)
    }

    @Test
    fun previewNeverExceedsItsAvailableWidth() {
        assertEquals(
            60,
            profilePictureViewerSizeDp(screenWidthDp = 100, screenHeightDp = 200),
        )
    }
}
