package quiz.thaton3app.nazo.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Geometry contracts for the Profile cover banner (Phase 1): the 90/10
 * avatar straddle over the band's bottom edge, the spacer that preserves the
 * old 16.dp avatar-to-username rhythm, and the orientation-aware band
 * heights. Pure math over [Dp] values — plain JVM tests.
 */
class ProfileCoverTest {

    @Test
    fun avatarOverhangIsExactlyTenPercentOfItsDiameter() {
        val avatar = 132.dp
        val overhang = coverOverhang(avatar)
        // 10% of the circle hangs below the band edge…
        assertEquals(13.2f, overhang.value, 0.01f)
        // …and the remaining 90% stays inside it.
        assertEquals(avatar.value - overhang.value, avatar.value * 0.90f, 0.01f)
    }

    @Test
    fun contentGapRestoresTheOriginalSixteenDpRhythm() {
        // The band pushes the avatar's bottom edge down by the overhang, so
        // the spacer must be overhang + the old gap to keep the username row
        // exactly 16.dp below the avatar — same rhythm as the pre-banner
        // Box(padding bottom = 16.dp) layout.
        val gap = coverContentGap(132.dp)
        assertEquals(13.2f + 16f, gap.value, 0.01f)
        assertEquals(16f, gap.value - coverOverhang(132.dp).value, 0.01f)
    }

    @Test
    fun coverHeightIsResponsiveAndClampedPerOrientation() {
        // Portrait: 34 % of screen height, clamped to 220–280.dp. The
        // reduced header moves the avatar higher without losing the cover's
        // visual prominence.
        assertEquals(220f, coverHeight(isLandscape = false, screenHeightDp = 600).value, 0.01f)
        assertEquals(272f, coverHeight(isLandscape = false, screenHeightDp = 800).value, 0.01f)
        assertEquals(280f, coverHeight(isLandscape = false, screenHeightDp = 900).value, 0.01f)
        // Landscape: 34 %, clamped 110–170.dp to preserve room for the
        // avatar and the right-side statistics/menu column.
        assertEquals(110f, coverHeight(isLandscape = true, screenHeightDp = 300).value, 0.01f)
        assertEquals(122.4f, coverHeight(isLandscape = true, screenHeightDp = 360).value, 0.01f)
        assertEquals(170f, coverHeight(isLandscape = true, screenHeightDp = 500).value, 0.01f)
    }

    @Test
    fun landscapeCoverShiftApproachesCutoutWithoutMovingOrNarrowingTheRightPane() {
        val narrow = landscapeProfileSpacing(screenWidthDp = 600)
        assertEquals(24f, narrow.startPadding.value, 0.001f)
        assertEquals(32f, narrow.columnSpacing.value, 0.001f)

        val normal = landscapeProfileSpacing(screenWidthDp = 800)
        assertEquals(16f, normal.startPadding.value, 0.001f) // cover/avatar −8dp
        assertEquals(40f, normal.columnSpacing.value, 0.001f)

        val wide = landscapeProfileSpacing(screenWidthDp = 1200)
        assertEquals(9f, wide.startPadding.value, 0.001f) // clamped −15dp
        assertEquals(47f, wide.columnSpacing.value, 0.001f)

        // The exchanged spacing always totals 56dp. Consequently the left
        // column keeps its old width and the right column keeps its old x edge.
        listOf(narrow, normal, wide).forEach { spacing ->
            assertEquals(
                56f,
                spacing.startPadding.value + spacing.columnSpacing.value,
                0.001f,
            )
        }
        assertEquals(8f, LandscapeRightColumnTopPadding.value, 0f)
    }

    @Test
    fun previewAspectRatioMirrorsTheLayoutMath() {
        // The cover preview dialog's frame must match the real banner pixel
        // for pixel, or "what you see is what you get" would be a lie.
        // Portrait: cover width (screen − 24.dp for the 12.dp gutters) over
        // the responsive height (640.dp screen → 34 % → floor 220.dp).
        assertEquals(
            336f / 220f,
            coverPreviewAspectRatio(
                screenWidthDp = 360,
                screenHeightDp = 640,
                isLandscape = false,
            ),
            0.001f,
        )
        // Landscape 40/60 split: (screen − 80 combined outer/gap spacing)
        // × 0.4 over the responsive height (360.dp → 34 % → 122.4.dp).
        assertEquals(
            288f / 122.4f,
            coverPreviewAspectRatio(
                screenWidthDp = 800,
                screenHeightDp = 360,
                isLandscape = true,
            ),
            0.01f,
        )
        // Degenerate split-screen slivers clamp to 1:1 instead of going
        // taller than wide (or negative).
        assertEquals(
            1f,
            coverPreviewAspectRatio(
                screenWidthDp = 100,
                screenHeightDp = 300,
                isLandscape = true,
            ),
            0f,
        )
    }
}
