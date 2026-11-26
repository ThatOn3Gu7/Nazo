package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDifficultyTest {

    private fun attempt(
        correct: Boolean,
        remaining: Float = 0.7f,
        hint: Boolean = false,
        timedOut: Boolean = false,
    ) = AdaptiveAttempt(correct, remaining, hint, timedOut)

    private fun question(id: Int, difficulty: String) = Question(
        id = id,
        anime = "Test",
        difficulty = difficulty,
        text = "Question $id",
        options = listOf("A", "B", "C", "D"),
        correctAnswer = "A",
    )

    @Test
    fun topicMasteryChoosesAHumaneStartingPoint() {
        fun mastery(status: TopicMasteryStatus) = TopicMastery(
            topic = "Naruto",
            answered = 8,
            correct = 6,
            accuracyPercent = 75,
            recentAccuracyPercent = 75,
            confidencePercent = 70,
            trendDeltaPercent = 0,
            status = status,
        )

        assertEquals(AdaptiveTier.MEDIUM, AdaptiveDifficulty.startingState(null).tier)
        assertEquals(AdaptiveTier.HARD, AdaptiveDifficulty.startingState(mastery(TopicMasteryStatus.Strong)).tier)
        assertEquals(AdaptiveTier.EASY, AdaptiveDifficulty.startingState(mastery(TopicMasteryStatus.NeedsPractice)).tier)
        assertEquals(AdaptiveTier.MEDIUM, AdaptiveDifficulty.startingState(mastery(TopicMasteryStatus.Improving)).tier)
    }

    @Test
    fun twoFastCleanAnswersRaiseTheTargetOnce() {
        val first = AdaptiveDifficulty.recordAttempt(
            AdaptiveQuizState(tier = AdaptiveTier.MEDIUM),
            attempt(correct = true, remaining = 0.8f),
        )
        assertEquals(AdaptiveTier.MEDIUM, first.tier)
        assertEquals(1, first.strongStreak)

        val second = AdaptiveDifficulty.recordAttempt(
            first,
            attempt(correct = true, remaining = 0.6f),
        )
        assertEquals(AdaptiveTier.HARD, second.tier)
        assertEquals(0, second.strongStreak)
        assertEquals(1, second.shiftCount)
        assertEquals(AdaptiveReason.StrongPace, second.reason)
    }

    @Test
    fun aMissOrTimeoutEasesTheNextTargetImmediately() {
        val hard = AdaptiveQuizState(tier = AdaptiveTier.HARD)
        val miss = AdaptiveDifficulty.recordAttempt(hard, attempt(correct = false))
        assertEquals(AdaptiveTier.MEDIUM, miss.tier)
        assertEquals(AdaptiveReason.RecentMiss, miss.reason)

        val timeout = AdaptiveDifficulty.recordAttempt(
            hard,
            attempt(correct = false, remaining = 0f, timedOut = true),
        )
        assertEquals(AdaptiveTier.MEDIUM, timeout.tier)
        assertEquals(AdaptiveReason.TimedOut, timeout.reason)
    }

    @Test
    fun hintsAndSlowCorrectAnswersNeverRaiseDifficulty() {
        val oneStrong = AdaptiveQuizState(tier = AdaptiveTier.MEDIUM, strongStreak = 1)
        val hinted = AdaptiveDifficulty.recordAttempt(
            oneStrong,
            attempt(correct = true, remaining = 0.9f, hint = true),
        )
        assertEquals(AdaptiveTier.MEDIUM, hinted.tier)
        assertEquals(0, hinted.strongStreak)
        assertEquals(AdaptiveReason.HintSupport, hinted.reason)

        val slow = AdaptiveDifficulty.recordAttempt(
            oneStrong,
            attempt(correct = true, remaining = 0.2f),
        )
        assertEquals(AdaptiveTier.MEDIUM, slow.tier)
        assertEquals(0, slow.strongStreak)
        assertEquals(AdaptiveReason.Steady, slow.reason)
    }

    @Test
    fun tierChangesClampAtBothEnds() {
        val easy = AdaptiveDifficulty.recordAttempt(
            AdaptiveQuizState(tier = AdaptiveTier.EASY),
            attempt(correct = false),
        )
        assertEquals(AdaptiveTier.EASY, easy.tier)
        assertEquals(0, easy.shiftCount)

        val topWithOneStrong = AdaptiveDifficulty.recordAttempt(
            AdaptiveQuizState(tier = AdaptiveTier.OTAKU_MASTER),
            attempt(correct = true),
        )
        val topWithTwoStrong = AdaptiveDifficulty.recordAttempt(
            topWithOneStrong,
            attempt(correct = true),
        )
        assertEquals(AdaptiveTier.OTAKU_MASTER, topWithTwoStrong.tier)
        assertEquals(0, topWithTwoStrong.shiftCount)
    }

    @Test
    fun nextQuestionUsesClosestTierWithoutChangingAnsweredPrefix() {
        val questions = listOf(
            question(1, "Medium"),
            question(2, "Easy"),
            question(3, "Otaku Master"),
            question(4, "Hard"),
        )

        val reordered = AdaptiveDifficulty.prioritizeNextQuestion(
            questions = questions,
            answeredQuestionIndex = 0,
            target = AdaptiveTier.HARD,
        )

        assertEquals(1, reordered[0].id)
        assertEquals(4, reordered[1].id)
        assertEquals(setOf(2, 3), reordered.drop(2).map { it.id }.toSet())
    }

    @Test
    fun localCandidateSequenceIsBalancedAndStartsAtMedium() {
        val sequence = AdaptiveDifficulty.candidateTierSequence(10)
        assertEquals(10, sequence.size)
        assertEquals(AdaptiveTier.MEDIUM, sequence.first())
        AdaptiveTier.entries.forEach { tier ->
            assertTrue(sequence.count { it == tier } in 2..3)
        }
    }
}
