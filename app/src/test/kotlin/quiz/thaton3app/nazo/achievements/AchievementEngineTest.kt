package quiz.thaton3app.nazo.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.data.QuizStats

class AchievementEngineTest {

    @Test
    fun `collection keeps every original badge and adds eight distinct tiers`() {
        val achievements = AchievementEngine.compute(QuizStats(), false, 0)
        val ids = achievements.map { it.id }
        val originalIds = setOf(
            "first_game", "ten_games", "fifty_games", "hundred_answers",
            "sharpshooter", "flawless", "streak_3", "streak_7", "elite",
            "daily_first", "daily_seven", "explorer",
        )
        val newIds = setOf(
            "combo_3", "combo_5", "combo_10", "combo_15",
            "hundred_games", "thousand_answers", "daily_thirty", "streak_30",
        )

        assertEquals(20, achievements.size)
        assertEquals(20, ids.toSet().size)
        assertTrue(ids.containsAll(originalIds))
        assertTrue(ids.containsAll(newIds))
    }

    @Test
    fun `combo tiers unlock progressively through the maximum quiz length`() {
        val atFourteen = AchievementEngine.compute(
            stats = QuizStats(bestCorrectCombo = 14),
            hasPerfectQuiz = false,
            dailiesCompleted = 0,
        ).associateBy { it.id }

        assertTrue(atFourteen.getValue("combo_3").unlocked)
        assertTrue(atFourteen.getValue("combo_5").unlocked)
        assertTrue(atFourteen.getValue("combo_10").unlocked)
        assertFalse(atFourteen.getValue("combo_15").unlocked)
        assertEquals("14 / 15 combo", atFourteen.getValue("combo_15").progressText)

        val atFifteen = AchievementEngine.compute(
            stats = QuizStats(bestCorrectCombo = 15),
            hasPerfectQuiz = false,
            dailiesCompleted = 0,
        ).associateBy { it.id }
        assertTrue(atFifteen.getValue("combo_15").unlocked)
    }

    @Test
    fun `long term tiers use existing totals and daily counts`() {
        val achievements = AchievementEngine.compute(
            stats = QuizStats(
                totalQuizzes = 100,
                totalQuestionsAnswered = 1_000,
                bestStreakDays = 30,
            ),
            hasPerfectQuiz = false,
            dailiesCompleted = 30,
        ).associateBy { it.id }

        assertTrue(achievements.getValue("hundred_games").unlocked)
        assertTrue(achievements.getValue("thousand_answers").unlocked)
        assertTrue(achievements.getValue("daily_thirty").unlocked)
        assertTrue(achievements.getValue("streak_30").unlocked)
    }
}
