package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun comboFeedbackEscalatesFromSparkToSuperCombo() {
        assertEquals(QuizComboTier.Spark, quizComboTier(2))
        assertEquals("SPARKED", quizComboCallout(2))
        assertEquals("HEATING UP", quizComboCallout(3))
        assertEquals(QuizComboTier.Blaze, quizComboTier(4))
        assertEquals("BLAZING", quizComboCallout(5))
        assertEquals(QuizComboTier.Inferno, quizComboTier(6))
        assertEquals("MAX HEAT", quizComboCallout(9))
        assertEquals(QuizComboTier.Super, quizComboTier(10))
        assertEquals("SUPER COMBO", quizComboCallout(10))
        assertEquals("MYTHIC", quizComboCallout(12))
        assertEquals("PERFECT STORM", quizComboCallout(15))

        assertEquals(2, quizComboMilestoneStreak(2))
        assertNull(quizComboMilestoneStreak(5))
        assertEquals(6, quizComboMilestoneStreak(6))
        assertEquals(10, quizComboMilestoneStreak(10))
        assertEquals(0f, quizComboHeat(2), 0f)
        assertEquals(0.425f, quizComboHeat(6), 0.0001f)
        assertEquals(0.85f, quizComboHeat(10), 0.0001f)
        assertEquals(1f, quizComboHeat(15), 0f)
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
    fun progressPathMarksEscalatingCombosHalfwayAndTheOrdinaryFinish() {
        val milestones = quizProgressMilestones(
            totalQuestions = 10,
            comboMilestonesByQuestion = mapOf(
                1 to 2,
                2 to 3, // ordinary stacked answers do not become path milestones
                5 to 6,
                9 to 10,
                -1 to 2,
                10 to 10,
            ),
            hasBossFinale = false,
        )

        assertEquals(
            listOf(
                QuizProgressMilestone(QuizProgressMilestoneKind.Combo, 0.2f, comboStreak = 2),
                QuizProgressMilestone(QuizProgressMilestoneKind.Halfway, 0.5f),
                QuizProgressMilestone(QuizProgressMilestoneKind.Combo, 0.6f, comboStreak = 6),
                QuizProgressMilestone(QuizProgressMilestoneKind.Combo, 1f, comboStreak = 10),
                QuizProgressMilestone(QuizProgressMilestoneKind.Final, 1f),
            ),
            milestones,
        )
    }

    @Test
    fun bossProgressPathReservesTheFinalQuestionAndFinish() {
        val milestones = quizProgressMilestones(
            totalQuestions = 10,
            comboMilestonesByQuestion = emptyMap(),
            hasBossFinale = true,
        )

        assertEquals(
            listOf(
                QuizProgressMilestone(QuizProgressMilestoneKind.Halfway, 0.5f),
                QuizProgressMilestone(QuizProgressMilestoneKind.Final, 0.9f),
                QuizProgressMilestone(QuizProgressMilestoneKind.Boss, 1f),
            ),
            milestones,
        )
        assertTrue(
            quizProgressMilestones(0, mapOf(0 to 2), hasBossFinale = true).isEmpty()
        )
    }

    @Test
    fun advancingRestoresPortraitScrollBeforeTheNextQuestion() {
        assertTrue(
            quizAdvanceShouldRestoreTop(
                scrollOffset = 480,
                landscape = false,
                hasNextQuestion = true,
            )
        )
        assertFalse(
            quizAdvanceShouldRestoreTop(0, landscape = false, hasNextQuestion = true)
        )
        assertFalse(
            quizAdvanceShouldRestoreTop(480, landscape = true, hasNextQuestion = true)
        )
        assertFalse(
            quizAdvanceShouldRestoreTop(480, landscape = false, hasNextQuestion = false)
        )
        assertEquals(260, quizAdvanceScrollDurationMillis(motionAllowed = true))
        assertEquals(0, quizAdvanceScrollDurationMillis(motionAllowed = false))
    }

    @Test
    fun decorativeQuizMotionWaitsForTheLaunchCoverAndReducedMotion() {
        assertTrue(quizDecorativeMotionAllowed(reduceMotion = false, hostAllowsMotion = true))
        assertTrue(!quizDecorativeMotionAllowed(reduceMotion = false, hostAllowsMotion = false))
        assertTrue(!quizDecorativeMotionAllowed(reduceMotion = true, hostAllowsMotion = true))
    }

    @Test
    fun questionStageEntranceIsOrderedAndNeverReplaysRestoredOrReducedMotionState() {
        assertEquals(0, questionEntranceDelayMillis(QuestionEntranceLayer.Progress))
        assertTrue(
            questionEntranceDelayMillis(QuestionEntranceLayer.Progress) <
                questionEntranceDelayMillis(QuestionEntranceLayer.Prompt)
        )
        assertTrue(
            questionEntranceDelayMillis(QuestionEntranceLayer.Prompt) <
                questionEntranceDelayMillis(QuestionEntranceLayer.Answers)
        )
        assertTrue(
            questionEntranceDelayMillis(QuestionEntranceLayer.Answers) <
                questionEntranceDelayMillis(QuestionEntranceLayer.Timer)
        )

        assertTrue(
            questionStageEntranceShouldPlay(
                previousQuestionIndex = 0,
                currentQuestionIndex = 1,
                motionAllowed = true,
                restoredGrade = false,
            )
        )
        assertFalse(
            questionStageEntranceShouldPlay(1, 1, motionAllowed = true, restoredGrade = false)
        )
        assertFalse(
            questionStageEntranceShouldPlay(0, 1, motionAllowed = false, restoredGrade = false)
        )
        assertFalse(
            questionStageEntranceShouldPlay(0, 1, motionAllowed = true, restoredGrade = true)
        )
    }

    @Test
    fun expressiveProgressWaveIsPeriodicAndReturnsToItsRestShape() {
        val wavelength = 20f
        assertEquals(0f, quizProgressWaveOffset(0f, wavelength, 0f), 0.0001f)
        assertEquals(1f, quizProgressWaveOffset(5f, wavelength, 0f), 0.0001f)
        assertEquals(0f, quizProgressWaveOffset(10f, wavelength, 0f), 0.0001f)
        assertEquals(-1f, quizProgressWaveOffset(15f, wavelength, 0f), 0.0001f)
        assertEquals(
            quizProgressWaveOffset(7f, wavelength, 0f),
            quizProgressWaveOffset(7f, wavelength, 1f),
            0.0001f,
        )
        assertEquals(-1f, quizProgressWaveOffset(0f, wavelength, 0.25f), 0.0001f)
        assertEquals(0f, quizProgressWaveOffset(10f, 0f, 0f), 0f)
    }

    @Test
    fun restrainedQuestionMotionMirrorsDirection() {
        assertEquals(80, questionTransitionOffset(width = 1_000, direction = 1))
        assertEquals(-80, questionTransitionOffset(width = 1_000, direction = -1))
        assertEquals(0, questionTransitionOffset(width = 1_000, direction = 0))
    }

    @Test
    fun answerChoreographyKeepsLearningAndActionInOrder() {
        assertTrue(!answerExplanationVisible(AnswerChoreographyStage.Choosing))
        assertTrue(!answerExplanationVisible(AnswerChoreographyStage.Graded))
        assertTrue(answerExplanationVisible(AnswerChoreographyStage.Explained))
        assertTrue(answerExplanationVisible(AnswerChoreographyStage.ActionReady))

        assertTrue(!answerActionVisible(AnswerChoreographyStage.Explained))
        assertTrue(answerActionVisible(AnswerChoreographyStage.ActionReady))
        assertTrue(AnswerExplanationDelayMs < AnswerActionDelayMs)
    }

    @Test
    fun explanationTethersToThePickedAnswerOrTheRevealedTimeoutAnswer() {
        assertTrue(
            explanationTetherAnchor(
                reveal = true,
                selected = true,
                correctAnswer = false,
                timeUp = false,
            )
        )
        assertTrue(
            explanationTetherAnchor(
                reveal = true,
                selected = true,
                correctAnswer = true,
                timeUp = false,
            )
        )
        assertFalse(
            explanationTetherAnchor(
                reveal = true,
                selected = false,
                correctAnswer = true,
                timeUp = false,
            )
        )
        assertTrue(
            explanationTetherAnchor(
                reveal = true,
                selected = false,
                correctAnswer = true,
                timeUp = true,
            )
        )
        assertFalse(
            explanationTetherAnchor(
                reveal = true,
                selected = true,
                correctAnswer = false,
                timeUp = true,
            )
        )
        assertFalse(
            explanationTetherAnchor(
                reveal = false,
                selected = true,
                correctAnswer = false,
                timeUp = false,
            )
        )
    }

    @Test
    fun answerOutcomeStagingPrioritizesThePickedAndCorrectOptions() {
        assertEquals(0, answerOptionRevealDelayMillis(selected = true, correctAnswer = false))
        assertEquals(90, answerOptionRevealDelayMillis(selected = false, correctAnswer = true))
        assertEquals(120, answerOptionRevealDelayMillis(selected = false, correctAnswer = false))

        assertEquals(
            0.58f,
            answerOptionEmphasis(reveal = true, selected = false, correctAnswer = false),
            0f,
        )
        assertEquals(
            1f,
            answerOptionEmphasis(reveal = true, selected = false, correctAnswer = true),
            0f,
        )
        assertEquals(
            1f,
            answerOptionEmphasis(reveal = false, selected = false, correctAnswer = false),
            0f,
        )
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
