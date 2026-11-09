package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure contracts for gameplay polish; none of these values affect grading. */
class ActiveQuizPolishTest {

    @Test
    fun comboBuildsOnlyAcrossConsecutiveCorrectAnswers() {
        var streak = 0
        streak = nextQuizComboStreak(streak, correct = true)
        assertEquals(1, streak)
        assertNull(quizComboLabel(streak))

        streak = nextQuizComboStreak(streak, correct = true)
        assertEquals(2, streak)
        assertEquals("2× COMBO", quizComboLabel(streak))

        streak = nextQuizComboStreak(streak, correct = true)
        assertEquals("3× COMBO", quizComboLabel(streak))

        streak = nextQuizComboStreak(streak, correct = false)
        assertEquals(0, streak)
        assertNull(quizComboLabel(streak))
    }

    @Test
    fun questionProgressIsSafeAndClamped() {
        assertEquals(0f, quizProgressFraction(0, 0), 0f)
        assertEquals(0.2f, quizProgressFraction(0, 5), 0.0001f)
        assertEquals(0.6f, quizProgressFraction(2, 5), 0.0001f)
        assertEquals(1f, quizProgressFraction(99, 5), 0f)
        assertEquals(0f, quizProgressFraction(-2, 5), 0f)
    }

    @Test
    fun restrainedQuestionMotionMirrorsDirection() {
        assertEquals(80, questionTransitionOffset(width = 1_000, direction = 1))
        assertEquals(-80, questionTransitionOffset(width = 1_000, direction = -1))
        assertEquals(0, questionTransitionOffset(width = 1_000, direction = 0))
    }

    @Test
    fun answerFeedbackAlwaysReturnsToRest() {
        assertEquals(0f, wrongAnswerShakeOffset(0f), 0.0001f)
        assertEquals(0f, wrongAnswerShakeOffset(1f), 0.0001f)
        assertTrue(wrongAnswerShakeOffset(1f / 12f) > 0f)
        assertTrue(wrongAnswerShakeOffset(3f / 12f) < 0f)

        assertEquals(1f, correctAnswerPulseScale(0f), 0.0001f)
        assertEquals(1.018f, correctAnswerPulseScale(0.5f), 0.0001f)
        assertEquals(1f, correctAnswerPulseScale(1f), 0.0001f)
    }
}
