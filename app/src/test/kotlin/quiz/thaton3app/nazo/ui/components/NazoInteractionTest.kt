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
    fun expressivePrototypeMorphsInsteadOfUniformlyScaling() {
        val rest = nazoExpressivePressVisuals(0f)
        val held = nazoExpressivePressVisuals(1f)

        assertEquals(1f, rest.horizontalScale, 0f)
        assertEquals(1f, rest.verticalScale, 0f)
        assertTrue(held.horizontalScale < rest.horizontalScale)
        assertTrue(held.verticalScale < held.horizontalScale)
        assertTrue(held.cornerRadiusDp > rest.cornerRadiusDp)
        assertTrue(held.elevationDp < rest.elevationDp)
        assertTrue(held.overlayAlpha > rest.overlayAlpha)
    }

    @Test
    fun expressiveGeometryAndQuickTapTimingStayBounded() {
        assertEquals(
            nazoExpressivePressVisuals(0f),
            nazoExpressivePressVisuals(-1f),
        )
        assertEquals(
            nazoExpressivePressVisuals(1f),
            nazoExpressivePressVisuals(2f),
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
