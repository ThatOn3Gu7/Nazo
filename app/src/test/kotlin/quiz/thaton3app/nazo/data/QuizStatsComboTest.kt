package quiz.thaton3app.nazo.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuizStatsComboTest {

    @Test
    fun `record keeps the longest consecutive-correct run`() {
        val questions = List(8) { question(it) }
        val answers = listOf("A", "A", "B", "A", "A", "A", null, "A")

        val updated = QuizStats().record("Medium", questions, answers)

        assertEquals(3, updated.bestCorrectCombo)
    }

    @Test
    fun `a shorter later run never lowers the saved best combo`() {
        val questions = List(4) { question(it) }
        val updated = QuizStats(bestCorrectCombo = 10).record(
            difficulty = "Hard",
            questions = questions,
            userAnswers = listOf("A", "A", "B", "A"),
        )

        assertEquals(10, updated.bestCorrectCombo)
    }

    @Test
    fun `record adds earned bonus xp without changing answer totals`() {
        val updated = QuizStats(totalCorrect = 4, bonusXp = 10).record(
            difficulty = "Hard",
            questions = listOf(question(1)),
            userAnswers = listOf("A"),
            bonusXpEarned = 20,
        )

        assertEquals(5, updated.totalCorrect)
        assertEquals(30, updated.bonusXp)
    }

    @Test
    fun `guessing aggregate does not invent an ordered combo`() {
        val updated = QuizStats(bestCorrectCombo = 5).recordGuessing(
            difficulty = "Medium",
            topic = "Test Anime",
            answered = 8,
            correct = 8,
        )

        assertEquals(5, updated.bestCorrectCombo)
    }

    private fun question(index: Int) = Question(
        anime = "Test Anime",
        text = "Question $index",
        options = listOf("A", "B", "C", "D"),
        correctAnswer = "A",
    )
}
