package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePictureFlowTest {

    @Test
    fun portraitPreviewUsesAvailableSheetWidth() {
        assertEquals(
            320,
            profileImagePreviewSizeDp(screenWidthDp = 360, screenHeightDp = 800),
        )
    }

    @Test
    fun landscapePreviewIsHeightCappedToKeepActionsNearby() {
        val portrait = profileImagePreviewSizeDp(screenWidthDp = 360, screenHeightDp = 800)
        val landscape = profileImagePreviewSizeDp(screenWidthDp = 800, screenHeightDp = 360)

        assertEquals(158, landscape)
        assertTrue(landscape < portrait)
    }

    @Test
    fun largeLandscapeWindowsStillUseABoundedPreview() {
        assertEquals(
            320,
            profileImagePreviewSizeDp(screenWidthDp = 1280, screenHeightDp = 800),
        )
    }
}
