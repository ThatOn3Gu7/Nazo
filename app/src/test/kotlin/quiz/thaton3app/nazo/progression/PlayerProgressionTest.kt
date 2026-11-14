package quiz.thaton3app.nazo.progression

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import quiz.thaton3app.nazo.achievements.Achievement
import quiz.thaton3app.nazo.data.QuizStats

class PlayerProgressionTest {

    @Test
    fun `progress preserves existing xp formula including daily bonus`() {
        val progress = PlayerProgression.from(
            stats = QuizStats(totalQuizzes = 7, totalCorrect = 23),
            bonusXp = 35,
        )

        assertEquals(300, progress.totalXp)
        assertEquals(2, progress.level)
        assertEquals(100, progress.currentLevelXp)
        assertEquals(100, progress.xpToNextLevel)
        assertEquals(0.5f, progress.progressFraction)
    }

    @Test
    fun `exact threshold starts the next level at zero in-level xp`() {
        val progress = PlayerProgression.from(
            stats = QuizStats(totalQuizzes = 0, totalCorrect = 20),
        )

        assertEquals(200, progress.totalXp)
        assertEquals(2, progress.level)
        assertEquals(0, progress.currentLevelXp)
        assertEquals(200, progress.xpToNextLevel)
    }

    @Test
    fun `reward reports level crossing and only newly unlocked existing achievements`() {
        val locked = achievement(id = "first", title = "First Steps", unlocked = false)
        val alreadyUnlocked = achievement(id = "steady", title = "Steady", unlocked = true)
        val reward = PlayerProgression.rewardBetween(
            before = PlayerProgress(totalXp = 195, level = 1, currentLevelXp = 195, xpPerLevel = 200),
            after = PlayerProgress(totalXp = 210, level = 2, currentLevelXp = 10, xpPerLevel = 200),
            beforeAchievements = listOf(locked, alreadyUnlocked),
            afterAchievements = listOf(
                locked.copy(unlocked = true),
                alreadyUnlocked,
            ),
        )

        assertEquals(15, reward.xpEarned)
        assertTrue(reward.leveledUp)
        assertEquals(listOf("First Steps"), reward.unlockedAchievementTitles)
    }

    @Test
    fun `reward never invents a level-up or negative xp`() {
        val reward = PlayerProgression.rewardBetween(
            before = PlayerProgress(totalXp = 50, level = 1, currentLevelXp = 50, xpPerLevel = 200),
            after = PlayerProgress(totalXp = 45, level = 1, currentLevelXp = 45, xpPerLevel = 200),
            beforeAchievements = emptyList(),
            afterAchievements = emptyList(),
        )

        assertEquals(0, reward.xpEarned)
        assertFalse(reward.leveledUp)
        assertTrue(reward.unlockedAchievementTitles.isEmpty())
    }

    private fun achievement(id: String, title: String, unlocked: Boolean) = Achievement(
        id = id,
        title = title,
        description = "",
        icon = Icons.Filled.Star,
        unlocked = unlocked,
        progress = if (unlocked) 1f else 0f,
        progressText = "",
    )
}
