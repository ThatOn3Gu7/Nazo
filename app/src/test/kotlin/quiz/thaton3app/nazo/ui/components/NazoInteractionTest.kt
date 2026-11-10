package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NazoInteractionTest {

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
    fun reusableCornerPolicySupportsRoundedCardsAndFlatRows() {
        assertEquals(20f, nazoCornerMorphValue(20f, 10f, 0f), 0f)
        assertEquals(15f, nazoCornerMorphValue(20f, 10f, 0.5f), 0f)
        assertEquals(10f, nazoCornerMorphValue(20f, 10f, 1f), 0f)

        // A flat list row can move in the opposite direction without changing
        // the shared timing or introducing scale/layout policy.
        assertEquals(0f, nazoCornerMorphValue(0f, 12f, -1f), 0f)
        assertEquals(12f, nazoCornerMorphValue(0f, 12f, 2f), 0f)
    }

    @Test
    fun expressiveCornerGeometryStaysBounded() {
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
    }

    @Test
    fun quickTapPeaksAtRestrainedProgressWithoutPullingBackLongerPresses() {
        assertEquals(NazoExpressiveQuickTapPeak, nazoQuickTapCompletionTarget(0f), 0f)
        assertEquals(NazoExpressiveQuickTapPeak, nazoQuickTapCompletionTarget(0.4f), 0f)
        assertEquals(NazoExpressiveQuickTapPeak, nazoQuickTapCompletionTarget(0.72f), 0f)
        assertEquals(0.9f, nazoQuickTapCompletionTarget(0.9f), 0f)
        assertEquals(1f, nazoQuickTapCompletionTarget(2f), 0f)
    }

    @Test
    fun quickTapCompletionTimeIsProportionalAndStrictlyBounded() {
        assertEquals(NazoExpressiveQuickTapCompletionMs, nazoQuickTapCompletionDurationMs(0f))
        assertEquals(23, nazoQuickTapCompletionDurationMs(0.36f))
        assertEquals(1, nazoQuickTapCompletionDurationMs(0.71f))
        assertEquals(0, nazoQuickTapCompletionDurationMs(NazoExpressiveQuickTapPeak))
        assertEquals(0, nazoQuickTapCompletionDurationMs(1f))
    }

    @Test
    fun sharedPressTimingKeepsReleaseGradualAndCancellationSafe() {
        assertEquals(90, NazoExpressivePressInDurationMs)
        assertEquals(45, NazoExpressiveQuickTapCompletionMs)
        assertEquals(0.72f, NazoExpressiveQuickTapPeak, 0f)
        assertEquals(180, NazoExpressiveReleaseDurationMs)
        assertEquals(120, NazoExpressiveCancelDurationMs)
        assertTrue(NazoExpressiveReleaseDurationMs > NazoExpressiveCancelDurationMs)
    }
}
