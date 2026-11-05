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
        // Portrait: 44 % of screen height, clamped to the agreed 300–360.dp
        // band — small screens get the floor, tall screens the cap, and the
        // common case scales in between.
        assertEquals(300f, coverHeight(isLandscape = false, screenHeightDp = 600).value, 0.01f)
        assertEquals(352f, coverHeight(isLandscape = false, screenHeightDp = 800).value, 0.01f)
        assertEquals(360f, coverHeight(isLandscape = false, screenHeightDp = 900).value, 0.01f)
        // Landscape: 46 %, clamped 160–220.dp — responsive, and never the
        // portrait height (the left column must keep the stats side visible).
        assertEquals(160f, coverHeight(isLandscape = true, screenHeightDp = 300).value, 0.01f)
        assertEquals(165.6f, coverHeight(isLandscape = true, screenHeightDp = 360).value, 0.01f)
        assertEquals(220f, coverHeight(isLandscape = true, screenHeightDp = 500).value, 0.01f)
    }

    @Test
    fun previewAspectRatioMirrorsTheLayoutMath() {
        // The cover preview dialog's frame must match the real banner pixel
        // for pixel, or "what you see is what you get" would be a lie.
        // Portrait: cover width (screen − 24.dp for the 12.dp gutters) over
        // the responsive height (640.dp screen → 44 % → floor 300.dp).
        assertEquals(
            336f / 300f,
            coverPreviewAspectRatio(
                screenWidthDp = 360,
                screenHeightDp = 640,
                isLandscape = false,
            ),
            0.001f,
        )
        // Landscape 40/60 split: (screen − 48 − 32 spacing) × 0.4 over the
        // responsive height (360.dp window → 46 % → 165.6.dp).
        assertEquals(
            288f / 165.6f,
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
