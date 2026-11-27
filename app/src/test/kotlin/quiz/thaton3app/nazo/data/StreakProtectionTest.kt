package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import quiz.thaton3app.nazo.data.settings.FakeSharedPreferences
import quiz.thaton3app.nazo.data.settings.QuizStatsStore

class StreakProtectionTest {

    @Test
    fun everyFiveCompletedGamesEarnsOneToken() {
        var tokens = 0
        var progress = 0
        repeat(4) {
            val next = StreakProtection.afterCompletedGame(tokens, progress)
            tokens = next.tokens
            progress = next.completedGamesTowardNext
            assertEquals(0, next.earnedTokens)
        }

        val earned = StreakProtection.afterCompletedGame(tokens, progress)

        assertEquals(1, earned.tokens)
        assertEquals(0, earned.completedGamesTowardNext)
        assertEquals(1, earned.earnedTokens)
    }

    @Test
    fun inventoryCapsAtTwoWithoutBankingAHiddenThirdToken() {
        val capped = StreakProtection.afterCompletedGame(
            tokens = StreakProtection.MAX_TOKENS,
            completedGamesTowardNext = StreakProtection.GAMES_PER_TOKEN - 1,
        )

        assertEquals(StreakProtection.MAX_TOKENS, capped.tokens)
        assertEquals(StreakProtection.GAMES_PER_TOKEN - 1, capped.completedGamesTowardNext)
        assertEquals(0, capped.earnedTokens)
    }

    @Test
    fun quizStatsCompletionPersistsAnEarnedToken() {
        val question = Question(
            anime = "Bleach",
            text = "Earning question",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "A",
        )
        val updated = QuizStats(
            streakProtectionProgress = StreakProtection.GAMES_PER_TOKEN - 1,
        ).record("Medium", listOf(question), listOf("A"))

        assertEquals(1, updated.streakProtectionTokens)
        assertEquals(0, updated.streakProtectionProgress)
    }

    @Test
    fun guessingCompletionEarnsProtectionThroughTheSameProgress() {
        val updated = QuizStats(
            streakProtectionProgress = StreakProtection.GAMES_PER_TOKEN - 1,
        ).recordGuessing(
            difficulty = "Medium",
            topic = "One Piece",
            answered = 3,
            correct = 2,
        )

        assertEquals(1, updated.streakProtectionTokens)
        assertEquals(0, updated.streakProtectionProgress)
    }

    @Test
    fun oneTokenCoversOneCompleteMissedDay() {
        val result = StreakProtection.reconcile(
            currentStreakDays = 8,
            lastQuizEpochDay = 100,
            protectedEpochDay = null,
            tokens = 1,
            todayEpochDay = 102,
        )

        assertEquals(8, result.currentStreakDays)
        assertEquals(0, result.tokens)
        assertEquals(101L, result.protectedEpochDay)
        assertEquals(1, result.consumedTokens)
    }

    @Test
    fun twoTokensCanCoverTwoConsecutiveMissedDaysEvenWhenAppWasClosed() {
        val result = StreakProtection.reconcile(
            currentStreakDays = 12,
            lastQuizEpochDay = 100,
            protectedEpochDay = null,
            tokens = 2,
            todayEpochDay = 103,
        )

        assertEquals(12, result.currentStreakDays)
        assertEquals(0, result.tokens)
        assertEquals(102L, result.protectedEpochDay)
        assertEquals(2, result.consumedTokens)
    }

    @Test
    fun insufficientInventoryResetsWithoutWastingTokens() {
        val result = StreakProtection.reconcile(
            currentStreakDays = 12,
            lastQuizEpochDay = 100,
            protectedEpochDay = null,
            tokens = 1,
            todayEpochDay = 103,
        )

        assertEquals(0, result.currentStreakDays)
        assertEquals(1, result.tokens)
        assertEquals(null, result.protectedEpochDay)
        assertEquals(0, result.consumedTokens)
    }

    @Test
    fun protectedQuizCompletionContinuesTheStreakAndClearsTheCoveredDay() {
        val today = QuizStats.localEpochDay()
        val question = Question(
            anime = "Naruto",
            text = "Protected question",
            options = listOf("A", "B", "C", "D"),
            correctAnswer = "A",
        )
        val updated = QuizStats(
            totalQuizzes = 5,
            totalQuestionsAnswered = 5,
            totalCorrect = 5,
            currentStreakDays = 5,
            bestStreakDays = 5,
            lastQuizEpochDay = today - 2,
            streakProtectionTokens = 1,
        ).record("Medium", listOf(question), listOf("A"))

        assertEquals(6, updated.currentStreakDays)
        assertEquals(6, updated.bestStreakDays)
        assertEquals(0, updated.streakProtectionTokens)
        assertEquals(today, updated.lastQuizEpochDay)
        assertEquals(null, updated.protectedStreakEpochDay)
    }

    @Test
    fun storePersistsAutomaticConsumptionBeforeReturningStats() {
        val today = QuizStats.localEpochDay()
        val store = QuizStatsStore(FakeSharedPreferences())
        store.save(
            QuizStats(
                totalQuizzes = 6,
                currentStreakDays = 6,
                bestStreakDays = 6,
                lastQuizEpochDay = today - 2,
                streakProtectionTokens = 1,
            )
        )

        val protected = store.get()

        assertEquals(6, protected.currentStreakDays)
        assertEquals(0, protected.streakProtectionTokens)
        assertEquals(today - 1, protected.protectedStreakEpochDay)
        assertEquals(protected, store.get())
    }

    @Test
    fun clockMovingBackwardsNeverConsumesProtection() {
        val result = StreakProtection.reconcile(
            currentStreakDays = 4,
            lastQuizEpochDay = 100,
            protectedEpochDay = null,
            tokens = 1,
            todayEpochDay = 99,
        )

        assertEquals(4, result.currentStreakDays)
        assertEquals(1, result.tokens)
        assertEquals(0, result.consumedTokens)
    }
}
