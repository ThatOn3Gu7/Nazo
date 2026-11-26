package quiz.thaton3app.nazo.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizLaunchPortalTest {

    private val origin = QuizLaunchOrigin(
        left = 24f,
        top = 600f,
        right = 376f,
        bottom = 658f,
    )

    @Test
    fun portalStartsAtButtonAndCoversTheExactViewport() {
        val start = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 0f,
            initialCornerRadius = 18f,
        )
        assertEquals(24f, start.left, 0f)
        assertEquals(600f, start.top, 0f)
        assertEquals(352f, start.width, 0f)
        assertEquals(58f, start.height, 0f)
        assertEquals(18f, start.cornerRadius, 0f)

        val covered = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 1f,
            initialCornerRadius = 18f,
        )
        assertEquals(0f, covered.left, 0f)
        assertEquals(0f, covered.top, 0f)
        assertEquals(400f, covered.width, 0f)
        assertEquals(800f, covered.height, 0f)
        assertEquals(0f, covered.cornerRadius, 0f)
    }

    @Test
    fun coveredPortalFoldsIntoDestinationCentreBeforeDisappearing() {
        val end = quizLaunchPortalFrame(
            origin = origin,
            viewportWidth = 400f,
            viewportHeight = 800f,
            terminalDiameter = 58f,
            progress = 2f,
            initialCornerRadius = 18f,
        )

        assertEquals(171f, end.left, 0f)
        assertEquals(371f, end.top, 0f)
        assertEquals(58f, end.width, 0f)
        assertEquals(58f, end.height, 0f)
        assertEquals(29f, end.cornerRadius, 0f)
        assertEquals(0f, end.alpha, 0f)
    }

    @Test
    fun fullCoverHasAReadableRealTimeDwell() {
        // This is deliberately a coroutine delay rather than another tween, so
        // a 0.5x animator scale cannot halve the time available to read it.
        assertTrue(QuizLaunchPortalDwellMillis >= 700L)
    }

    @Test
    fun messageSettlesAtFullCoverAndCollapsesWithThePortal() {
        assertEquals(0f, quizLaunchPortalMessageAlpha(0f), 0f)
        assertEquals(1f, quizLaunchPortalMessageAlpha(1f), 0f)
        assertTrue(quizLaunchPortalMessageAlpha(1.5f) in 0f..1f)
        assertEquals(0f, quizLaunchPortalMessageAlpha(2f), 0f)
    }

    @Test
    fun speakerRingsBreatheSpinAndReturnSeamlessly() {
        val start = quizLaunchRingRadius(
            baseRadius = 100f,
            waveAmplitude = 8f,
            waveCount = 5,
            angleRadians = 0.37f,
            phaseFraction = 0f,
            direction = 1,
        )
        val moving = quizLaunchRingRadius(
            baseRadius = 100f,
            waveAmplitude = 8f,
            waveCount = 5,
            angleRadians = 0.37f,
            phaseFraction = 0.2f,
            direction = 1,
        )
        val fullTurn = quizLaunchRingRadius(
            baseRadius = 100f,
            waveAmplitude = 8f,
            waveCount = 5,
            angleRadians = 0.37f,
            phaseFraction = 1f,
            direction = 1,
        )
        val nextPoint = quizLaunchRingRadius(
            baseRadius = 100f,
            waveAmplitude = 8f,
            waveCount = 5,
            angleRadians = 0.9f,
            phaseFraction = 0.2f,
            direction = 1,
        )

        assertTrue(kotlin.math.abs(moving - start) > 0.1f)
        assertTrue(kotlin.math.abs(nextPoint - moving) > 0.1f)
        assertEquals(start, fullTurn, 0.0001f)
    }
}
