package quiz.thaton3app.nazo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultsStoryTest {

    @Test
    fun resultStoryRevealsScoreBreakdownRewardsAndActionsInOrder() {
        val timeline = quizResultStoryTimeline(hasRewards = true)

        assertTrue(timeline.headingAtMillis < timeline.scoreAtMillis)
        assertTrue(timeline.scoreAtMillis < timeline.breakdownAtMillis)
        assertTrue(timeline.breakdownAtMillis < timeline.rewardsAtMillis)
        assertTrue(timeline.rewardsAtMillis < timeline.actionsAtMillis)
        assertTrue(timeline.actionsAtMillis <= 1_300L)

        assertFalse(
            quizResultStoryShows(
                QuizResultStoryStage.Score,
                QuizResultStoryStage.Breakdown,
            )
        )
        assertTrue(
            quizResultStoryShows(
                QuizResultStoryStage.Rewards,
                QuizResultStoryStage.Breakdown,
            )
        )
        assertFalse(
            quizResultStoryShows(
                QuizResultStoryStage.Rewards,
                QuizResultStoryStage.Actions,
            )
        )
        assertTrue(
            quizResultStoryShows(
                QuizResultStoryStage.Actions,
                QuizResultStoryStage.Rewards,
            )
        )
    }

    @Test
    fun storyDoesNotHoldActionsForAnEmptyRewardChapter() {
        val withRewards = quizResultStoryTimeline(hasRewards = true)
        val withoutRewards = quizResultStoryTimeline(hasRewards = false)

        assertTrue(withoutRewards.actionsAtMillis < withRewards.actionsAtMillis)
        assertTrue(withoutRewards.actionsAtMillis >= withoutRewards.rewardsAtMillis)
    }

    @Test
    fun breakdownStaggerIsShortAndReducedMotionIsImmediate() {
        assertEquals(
            QuizResultStoryStage.Arriving,
            quizResultStoryInitialStage(playEntranceSequence = true, reduceMotion = false),
        )
        assertEquals(
            QuizResultStoryStage.Actions,
            quizResultStoryInitialStage(playEntranceSequence = true, reduceMotion = true),
        )
        assertEquals(
            QuizResultStoryStage.Actions,
            quizResultStoryInitialStage(playEntranceSequence = false, reduceMotion = false),
        )
        assertEquals(0, quizResultStatDelayMillis(index = 0, motionAllowed = true))
        assertEquals(80, quizResultStatDelayMillis(index = 1, motionAllowed = true))
        assertEquals(160, quizResultStatDelayMillis(index = 2, motionAllowed = true))
        assertEquals(0, quizResultStatDelayMillis(index = 2, motionAllowed = false))
    }

    @Test
    fun mistakeRematchActionUsesAClearSingularOrPluralLabel() {
        assertEquals("Retry 1 Miss", mistakeRematchLabel(1))
        assertEquals("Retry 3 Misses", mistakeRematchLabel(3))
    }

    @Test
    fun dailyLadderGetsADefinitiveFinishAndHomeAction() {
        assertEquals("DAILY LADDER", quizResultEyebrow(daily = true, modeLabel = null))
        assertEquals(
            "Daily Ladder Cleared",
            quizResultHeading(
                daily = true,
                modeLabel = null,
                difficulty = "Daily",
                score = 3,
            ),
        )
        assertEquals("Back to Home", quizResultReturnLabel(daily = true, modeLabel = null))
    }

    @Test
    fun ordinaryResultLabelsRemainUnchangedByTheDailyPath() {
        assertEquals("FOCUSED QUIZ", quizResultEyebrow(daily = false, modeLabel = null))
        assertEquals(
            "Practice Complete",
            quizResultHeading(
                daily = false,
                modeLabel = null,
                difficulty = "Practice",
                score = 2,
            ),
        )
        assertEquals("Play Another Quiz", quizResultReturnLabel(daily = false, modeLabel = null))
        assertEquals(
            "Adaptive Quiz Complete",
            quizResultHeading(
                daily = false,
                modeLabel = null,
                difficulty = "Adaptive",
                score = 4,
            ),
        )
    }

    @Test
    fun curatedPackResultKeepsItsOwnIdentityAndReplayAction() {
        assertEquals(
            "CURATED PACK REPORT",
            quizResultEyebrow(daily = false, modeLabel = "Challenge Pack"),
        )
        assertEquals(
            "Challenge Pack Complete",
            quizResultHeading(
                daily = false,
                modeLabel = "Challenge Pack",
                difficulty = "Medium",
                score = 6,
            ),
        )
        assertEquals(
            "Replay This Pack",
            quizResultReturnLabel(daily = false, modeLabel = "Challenge Pack"),
        )
    }

    @Test
    fun averagePaceUsesTheFrozenRunDuration() {
        assertEquals("12.4s/q", quizResultPaceLabel(elapsedMillis = 61_999L, answered = 5))
        assertEquals("0.1s/q", quizResultPaceLabel(elapsedMillis = 149L, answered = 1))
        assertEquals("—", quizResultPaceLabel(elapsedMillis = 0L, answered = 5))
        assertEquals("—", quizResultPaceLabel(elapsedMillis = 10_000L, answered = 0))
    }
}
