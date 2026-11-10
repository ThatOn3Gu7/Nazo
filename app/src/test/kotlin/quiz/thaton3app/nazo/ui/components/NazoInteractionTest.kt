package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NazoInteractionTest {

    @Test
    fun enabledPressUsesRestrainedSharedScale() {
        assertTrue(NazoPressedScale in 0.96f..0.99f)
        assertEquals(
            NazoPressedScale,
            nazoPressTargetScale(
                pressed = true,
                enabled = true,
                reduceMotion = false,
            ),
            0f,
        )
    }

    @Test
    fun restDisabledAndReducedMotionNeverTransformControls() {
        assertEquals(1f, nazoPressTargetScale(false, true, false), 0f)
        assertEquals(1f, nazoPressTargetScale(true, false, false), 0f)
        assertEquals(1f, nazoPressTargetScale(true, true, true), 0f)
    }

    @Test
    fun customScaleIsClampedToSubtleRange() {
        assertEquals(0.9f, nazoPressTargetScale(true, true, false, 0.5f), 0f)
        assertEquals(1f, nazoPressTargetScale(true, true, false, 1.2f), 0f)
    }

    @Test
    fun expressivePracticePrototypeMorphsOnlyItsCornerGeometry() {
        val rest = nazoExpressiveCornerPercent(0f)
        val held = nazoExpressiveCornerPercent(1f)

        assertEquals(NazoPillRestCornerPercent, rest, 0f)
        assertEquals(NazoPillPressedCornerPercent, held, 0f)
        assertEquals(50f, rest, 0f)
        assertTrue(held < rest)
    }

    @Test
    fun expressiveCornerGeometryAndQuickTapTimingStayBounded() {
        assertEquals(
            nazoExpressiveCornerPercent(0f),
            nazoExpressiveCornerPercent(-1f),
            0f,
        )
        assertEquals(
            nazoExpressiveCornerPercent(1f),
            nazoExpressiveCornerPercent(2f),
            0f,
        )
        assertEquals(0, nazoQuickTapCompletionDurationMs(1f))
        assertTrue(
            nazoQuickTapCompletionDurationMs(0f) in
                1..NazoExpressiveQuickTapCompletionMs
        )
        assertTrue(
            nazoQuickTapCompletionDurationMs(0.75f) <
                nazoQuickTapCompletionDurationMs(0.25f)
        )
    }
}
