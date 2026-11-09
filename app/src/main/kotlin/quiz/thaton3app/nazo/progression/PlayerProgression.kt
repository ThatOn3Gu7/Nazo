package quiz.thaton3app.nazo.progression

import quiz.thaton3app.nazo.achievements.Achievement
import quiz.thaton3app.nazo.data.QuizStats

/**
 * The single presentation model for Nazo's existing XP and level rules.
 *
 * This intentionally derives everything from the stores that already exist.
 * It is not persisted and introduces no second progression system.
 */
data class PlayerProgress(
    val totalXp: Int,
    val level: Int,
    val currentLevelXp: Int,
    val xpPerLevel: Int,
) {
    val progressFraction: Float
        get() = if (xpPerLevel == 0) 0f
        else (currentLevelXp.toFloat() / xpPerLevel).coerceIn(0f, 1f)

    val xpToNextLevel: Int
        get() = (xpPerLevel - currentLevelXp).coerceAtLeast(0)
}

/** A transient summary of rewards that were already earned by a completed game. */
data class ProgressionReward(
    val xpEarned: Int,
    val previousLevel: Int,
    val progress: PlayerProgress,
    val unlockedAchievementTitles: List<String>,
) {
    val leveledUp: Boolean get() = progress.level > previousLevel
}

object PlayerProgression {
    const val XP_PER_LEVEL = 200

    /** Preserves Nazo's existing formula exactly: correct × 10 + games × 5 + Daily XP. */
    fun from(stats: QuizStats, bonusXp: Int = 0): PlayerProgress {
        val totalXp = stats.totalCorrect * 10 + stats.totalQuizzes * 5 + bonusXp
        return PlayerProgress(
            totalXp = totalXp,
            level = totalXp / XP_PER_LEVEL + 1,
            currentLevelXp = totalXp % XP_PER_LEVEL,
            xpPerLevel = XP_PER_LEVEL,
        )
    }

    /**
     * Compares two already-derived snapshots. Achievement events are inferred
     * from the existing rules for this completion only; no "seen" state is saved.
     */
    fun rewardBetween(
        before: PlayerProgress,
        after: PlayerProgress,
        beforeAchievements: List<Achievement>,
        afterAchievements: List<Achievement>,
    ): ProgressionReward {
        val previouslyUnlocked = beforeAchievements.asSequence()
            .filter { it.unlocked }
            .map(Achievement::id)
            .toSet()
        val newlyUnlocked = afterAchievements
            .filter { it.unlocked && it.id !in previouslyUnlocked }
            .map(Achievement::title)

        return ProgressionReward(
            xpEarned = (after.totalXp - before.totalXp).coerceAtLeast(0),
            previousLevel = before.level,
            progress = after,
            unlockedAchievementTitles = newlyUnlocked,
        )
    }
}
