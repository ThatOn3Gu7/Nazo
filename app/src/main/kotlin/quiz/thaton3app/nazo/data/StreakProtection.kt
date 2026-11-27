package quiz.thaton3app.nazo.data

import kotlin.math.max

/** Earned, bounded protection for one or more otherwise missed streak days. */
object StreakProtection {
    const val MAX_TOKENS = 2
    const val GAMES_PER_TOKEN = 5

    data class EarnedProgress(
        val tokens: Int,
        val completedGamesTowardNext: Int,
        val earnedTokens: Int,
    )

    data class Reconciliation(
        val currentStreakDays: Int,
        val tokens: Int,
        val protectedEpochDay: Long?,
        val consumedTokens: Int,
    )

    /** Every five completed stat-bearing games earns one shield, capped at two. */
    fun afterCompletedGame(
        tokens: Int,
        completedGamesTowardNext: Int,
    ): EarnedProgress {
        val safeTokens = tokens.coerceIn(0, MAX_TOKENS)
        val safeProgress = completedGamesTowardNext.coerceIn(0, GAMES_PER_TOKEN - 1)
        if (safeTokens == MAX_TOKENS) {
            return EarnedProgress(
                tokens = safeTokens,
                completedGamesTowardNext = safeProgress,
                earnedTokens = 0,
            )
        }
        val nextProgress = safeProgress + 1
        return if (nextProgress >= GAMES_PER_TOKEN) {
            EarnedProgress(
                tokens = safeTokens + 1,
                completedGamesTowardNext = 0,
                earnedTokens = 1,
            )
        } else {
            EarnedProgress(
                tokens = safeTokens,
                completedGamesTowardNext = nextProgress,
                earnedTokens = 0,
            )
        }
    }

    /**
     * Applies shields only when the available inventory can preserve the whole
     * gap. This avoids silently wasting a token after an absence that is still
     * long enough to break the streak.
     */
    fun reconcile(
        currentStreakDays: Int,
        lastQuizEpochDay: Long?,
        protectedEpochDay: Long?,
        tokens: Int,
        todayEpochDay: Long,
    ): Reconciliation {
        val safeTokens = tokens.coerceIn(0, MAX_TOKENS)
        if (currentStreakDays <= 0) {
            return Reconciliation(0, safeTokens, null, 0)
        }
        val effectiveLastDay = when {
            lastQuizEpochDay == null -> protectedEpochDay
            protectedEpochDay == null -> lastQuizEpochDay
            else -> max(lastQuizEpochDay, protectedEpochDay)
        } ?: return Reconciliation(0, safeTokens, null, 0)

        val dayGap = todayEpochDay - effectiveLastDay
        if (dayGap <= 1L) {
            // Includes a clock moving backwards: never destroy or spend a
            // shield because of timezone travel or a manual clock correction.
            return Reconciliation(
                currentStreakDays = currentStreakDays,
                tokens = safeTokens,
                protectedEpochDay = protectedEpochDay,
                consumedTokens = 0,
            )
        }

        val missedDays = (dayGap - 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (missedDays <= safeTokens) {
            return Reconciliation(
                currentStreakDays = currentStreakDays,
                tokens = safeTokens - missedDays,
                protectedEpochDay = todayEpochDay - 1L,
                consumedTokens = missedDays,
            )
        }

        return Reconciliation(
            currentStreakDays = 0,
            tokens = safeTokens,
            protectedEpochDay = null,
            consumedTokens = 0,
        )
    }
}
