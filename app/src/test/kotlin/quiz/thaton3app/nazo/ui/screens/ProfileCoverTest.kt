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
    fun bandHeightIsSubstantialAndTighterInLandscape() {
        // Portrait gets the tall band; landscape keeps the same straddle in
        // a shorter band so the two-column layout still breathes.
        assertEquals(160f, coverHeight(isLandscape = false).value, 0f)
        assertEquals(120f, coverHeight(isLandscape = true).value, 0f)
    }
}
