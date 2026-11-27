package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossQuestionTest {

    private fun question(id: Int, difficulty: String) = Question(
        id = id,
        anime = "Test",
        difficulty = difficulty,
        text = "Question $id",
        options = listOf("A", "B", "C", "D"),
        correctAnswer = "A",
    )

    @Test
    fun targetIsOneTierHigherAndClampedAtOtakuMaster() {
        assertEquals(AdaptiveTier.MEDIUM, BossQuestion.targetTier("Easy"))
        assertEquals(AdaptiveTier.HARD, BossQuestion.targetTier("Medium"))
        assertEquals(AdaptiveTier.OTAKU_MASTER, BossQuestion.targetTier("Hard"))
        assertEquals(AdaptiveTier.OTAKU_MASTER, BossQuestion.targetTier("Otaku Master"))
        assertEquals(
            AdaptiveTier.HARD,
            BossQuestion.targetTier("Adaptive", adaptiveTarget = AdaptiveTier.MEDIUM),
        )
    }

    @Test
    fun localDeckReservesTheClosestElevatedQuestionForLast() {
        val regular = listOf(question(1, "Medium"), question(2, "Medium"), question(3, "Medium"))
        val bosses = listOf(question(4, "Otaku Master"), question(5, "Hard"))

        val deck = BossQuestion.buildLocalDeck(
            regularCandidates = regular,
            bossCandidates = bosses,
            count = 4,
            enabled = true,
            difficulty = "Medium",
        )

        assertEquals(4, deck.size)
        assertEquals(5, deck.last().id)
        assertEquals("Hard", deck.last().difficulty)
        assertEquals(deck.size, deck.map { it.identity }.toSet().size)
    }

    @Test
    fun generatedDeckOnlyMovesTheChosenFinale() {
        val candidates = listOf(
            question(1, "Easy"),
            question(2, "Hard"),
            question(3, "Medium"),
        )

        val arranged = BossQuestion.arrangeGeneratedDeck(
            candidates = candidates,
            enabled = true,
            difficulty = "Medium",
        )

        assertEquals(listOf(1, 3, 2), arranged.map { it.id })
    }

    @Test
    fun generatedDeckPreservesThePromptedFinaleWhenTheTierIsAlreadyMaxed() {
        val promptedFinale = question(3, "Otaku Master")
        val reorderedByFreshness = listOf(
            promptedFinale,
            question(1, "Otaku Master"),
            question(2, "Otaku Master"),
        )

        val arranged = BossQuestion.arrangeGeneratedDeck(
            candidates = reorderedByFreshness,
            enabled = true,
            difficulty = "Otaku Master",
            preferredFinalIdentity = promptedFinale.identity,
        )

        assertEquals(listOf(1, 2, 3), arranged.map { it.id })
    }

    @Test
    fun bonusAndFinaleDetectionRequireTheSelectedFinalCorrectAnswer() {
        assertTrue(BossQuestion.isFinale(enabled = true, questionIndex = 4, questionCount = 5))
        assertFalse(BossQuestion.isFinale(enabled = true, questionIndex = 3, questionCount = 5))
        assertEquals(BossQuestion.BONUS_XP, BossQuestion.bonusXp(enabled = true, correct = true))
        assertEquals(0, BossQuestion.bonusXp(enabled = true, correct = false))
        assertEquals(0, BossQuestion.bonusXp(enabled = false, correct = true))
    }
}
