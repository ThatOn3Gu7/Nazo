package quiz.thaton3app.nazo.data

/**
 * Aggregate quiz statistics, persisted as a single JSON blob (see [QuizStatsStore]).
 *
 * All mutations are pure: [record] returns a NEW instance with the quiz folded in,
 * so the store can read -> transform -> write without any mutable shared state.
 *
 * Tracked insights:
 *  - total quizzes, questions answered, correct answers (overall accuracy)
 *  - durable bonus XP earned from optional challenges
 *  - current / best daily streak plus earned, automatically consumed protection
 *  - per-difficulty play count + correct/answered (accuracy per difficulty)
 *  - per-anime answered/correct plus a bounded recent outcome window for mastery
 *  - a bounded newest-first topic trail for personalized Home recommendations
 *  - best consecutive-correct combo completed within one recorded quiz run
 */
data class QuizStats(
    val totalQuizzes: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val totalCorrect: Int = 0,
    /** Durable bonus XP from optional challenges such as a cleared Boss Finale. */
    val bonusXp: Int = 0,
    val bestCorrectCombo: Int = 0,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastQuizEpochDay: Long? = null,
    val streakProtectionTokens: Int = 0,
    val streakProtectionProgress: Int = 0,
    /** Most recent missed day covered by an automatically consumed shield. */
    val protectedStreakEpochDay: Long? = null,
    val difficultyPlays: Map<String, Int> = emptyMap(),
    val difficultyCorrect: Map<String, Int> = emptyMap(),
    val difficultyAnswered: Map<String, Int> = emptyMap(),
    val animeAnswered: Map<String, Int> = emptyMap(),
    val animeCorrect: Map<String, Int> = emptyMap(),
    /** Last eight outcomes per topic, oldest to newest (`1` correct, `0` missed). */
    val animeRecentOutcomes: Map<String, String> = emptyMap(),
    /** Most recently played known topics, newest first, for personalized Home actions. */
    val recentTopics: List<String> = emptyList(),
) {
    fun record(
        difficulty: String,
        questions: List<Question>,
        userAnswers: List<String?>,
        bonusXpEarned: Int = 0,
    ): QuizStats {
        val today = localEpochDay()
        val reconciled = withStreakProtection(today)
        if (reconciled != this) {
            return reconciled.record(difficulty, questions, userAnswers, bonusXpEarned)
        }

        val newStreak = when {
            lastQuizEpochDay == today -> currentStreakDays          // already counted today
            lastQuizEpochDay == today - 1 -> currentStreakDays + 1  // consecutive day
            protectedStreakEpochDay == today - 1 -> currentStreakDays + 1
            else -> 1                                               // streak reset / first ever
        }
        val protection = StreakProtection.afterCompletedGame(
            tokens = streakProtectionTokens,
            completedGamesTowardNext = streakProtectionProgress,
        )

        val dPlays = difficultyPlays.toMutableMap()
        val dCorrect = difficultyCorrect.toMutableMap()
        val dAnswered = difficultyAnswered.toMutableMap()
        val aAnswered = animeAnswered.toMutableMap()
        val aCorrect = animeCorrect.toMutableMap()
        val aRecent = animeRecentOutcomes.toMutableMap()

        dPlays[difficulty] = dPlays.getOrDefault(difficulty, 0) + 1

        var answered = totalQuestionsAnswered
        var correct = totalCorrect
        var currentCombo = 0
        var runBestCombo = 0

        questions.forEachIndexed { i, q ->
            val ans = userAnswers.getOrNull(i)
            val isCorrect = ans != null && ans == q.correctAnswer
            answered++
            if (isCorrect) {
                correct++
                currentCombo++
                runBestCombo = maxOf(runBestCombo, currentCombo)
            } else {
                currentCombo = 0
            }
            dAnswered[difficulty] = dAnswered.getOrDefault(difficulty, 0) + 1
            if (isCorrect) dCorrect[difficulty] = dCorrect.getOrDefault(difficulty, 0) + 1
            val anime = q.anime.ifBlank { "Unknown" }
            aAnswered[anime] = aAnswered.getOrDefault(anime, 0) + 1
            if (isCorrect) aCorrect[anime] = aCorrect.getOrDefault(anime, 0) + 1
            aRecent[anime] = appendRecentOutcomes(
                existing = aRecent[anime].orEmpty(),
                outcomes = listOf(isCorrect),
            )
        }

        return copy(
            totalQuizzes = totalQuizzes + 1,
            totalQuestionsAnswered = answered,
            totalCorrect = correct,
            bonusXp = bonusXp + bonusXpEarned.coerceAtLeast(0),
            bestCorrectCombo = maxOf(bestCorrectCombo, runBestCombo),
            currentStreakDays = newStreak,
            bestStreakDays = maxOf(bestStreakDays, newStreak),
            lastQuizEpochDay = today,
            streakProtectionTokens = protection.tokens,
            streakProtectionProgress = protection.completedGamesTowardNext,
            protectedStreakEpochDay = null,
            difficultyPlays = dPlays,
            difficultyCorrect = dCorrect,
            difficultyAnswered = dAnswered,
            animeAnswered = aAnswered,
            animeCorrect = aCorrect,
            animeRecentOutcomes = aRecent,
            recentTopics = updateRecentTopics(recentTopics, questions.map(Question::anime)),
        )
    }

    /**
     * Folds in a COMPLETED guessing game. Rounds count as answered
     * questions, correct rounds as correct, and the game's topic is
     * credited as an anime — so guessing games feed the level/XP, streak,
     * difficulty breakdown and top-mastered list exactly like quizzes do
     * (one game = +1 play, +10 XP per correct round, +5 XP for the game).
     */
    fun recordGuessing(
        difficulty: String,
        topic: String,
        answered: Int,
        correct: Int,
        outcomes: List<Boolean> = emptyList(),
    ): QuizStats {
        val today = localEpochDay()
        val reconciled = withStreakProtection(today)
        if (reconciled != this) {
            return reconciled.recordGuessing(difficulty, topic, answered, correct, outcomes)
        }

        val newStreak = when {
            lastQuizEpochDay == today -> currentStreakDays          // already counted today
            lastQuizEpochDay == today - 1 -> currentStreakDays + 1  // consecutive day
            protectedStreakEpochDay == today - 1 -> currentStreakDays + 1
            else -> 1                                               // streak reset / first ever
        }
        val protection = StreakProtection.afterCompletedGame(
            tokens = streakProtectionTokens,
            completedGamesTowardNext = streakProtectionProgress,
        )

        val dPlays = difficultyPlays.toMutableMap()
        val dCorrect = difficultyCorrect.toMutableMap()
        val dAnswered = difficultyAnswered.toMutableMap()
        val aAnswered = animeAnswered.toMutableMap()
        val aCorrect = animeCorrect.toMutableMap()
        val aRecent = animeRecentOutcomes.toMutableMap()

        dPlays[difficulty] = dPlays.getOrDefault(difficulty, 0) + 1
        dAnswered[difficulty] = dAnswered.getOrDefault(difficulty, 0) + answered
        dCorrect[difficulty] = dCorrect.getOrDefault(difficulty, 0) + correct
        val anime = topic.ifBlank { "Unknown" }
        aAnswered[anime] = aAnswered.getOrDefault(anime, 0) + answered
        aCorrect[anime] = aCorrect.getOrDefault(anime, 0) + correct
        val recordedOutcomes = outcomes.take(answered.coerceAtLeast(0))
        if (recordedOutcomes.isNotEmpty()) {
            aRecent[anime] = appendRecentOutcomes(
                existing = aRecent[anime].orEmpty(),
                outcomes = recordedOutcomes,
            )
        }

        return copy(
            totalQuizzes = totalQuizzes + 1,
            totalQuestionsAnswered = totalQuestionsAnswered + answered,
            totalCorrect = totalCorrect + correct,
            currentStreakDays = newStreak,
            bestStreakDays = maxOf(bestStreakDays, newStreak),
            lastQuizEpochDay = today,
            streakProtectionTokens = protection.tokens,
            streakProtectionProgress = protection.completedGamesTowardNext,
            protectedStreakEpochDay = null,
            difficultyPlays = dPlays,
            difficultyCorrect = dCorrect,
            difficultyAnswered = dAnswered,
            animeAnswered = aAnswered,
            animeCorrect = aCorrect,
            animeRecentOutcomes = aRecent,
            recentTopics = updateRecentTopics(recentTopics, listOf(topic)),
        )
    }

    /**
     * Reconciles calendar time before any consumer displays or records stats.
     * Available shields cover complete missed days automatically. A shield is
     * spent only when the inventory can preserve the whole absence, so a long
     * break never wastes protection on a streak that must still reset.
     */
    fun withStreakProtection(today: Long = localEpochDay()): QuizStats {
        val reconciliation = StreakProtection.reconcile(
            currentStreakDays = currentStreakDays,
            lastQuizEpochDay = lastQuizEpochDay,
            protectedEpochDay = protectedStreakEpochDay,
            tokens = streakProtectionTokens,
            todayEpochDay = today,
        )
        if (
            reconciliation.currentStreakDays == currentStreakDays &&
            reconciliation.tokens == streakProtectionTokens &&
            reconciliation.protectedEpochDay == protectedStreakEpochDay
        ) {
            return this
        }
        return copy(
            currentStreakDays = reconciliation.currentStreakDays,
            streakProtectionTokens = reconciliation.tokens,
            protectedStreakEpochDay = reconciliation.protectedEpochDay,
        )
    }

    /** Backward-compatible name retained for existing callers and tests. */
    fun withStreakExpiry(today: Long = localEpochDay()): QuizStats =
        withStreakProtection(today)

    /** Last actual or shield-covered day participating in the current streak. */
    fun lastStreakActivityEpochDay(): Long? = when {
        lastQuizEpochDay == null -> protectedStreakEpochDay
        protectedStreakEpochDay == null -> lastQuizEpochDay
        else -> maxOf(lastQuizEpochDay, protectedStreakEpochDay)
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        internal const val RECENT_TOPIC_OUTCOME_LIMIT = 8
        internal const val RECENT_TOPIC_LIMIT = 6

        private fun updateRecentTopics(
            existing: List<String>,
            playedTopics: List<String>,
        ): List<String> {
            val updated = existing.asSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .take(RECENT_TOPIC_LIMIT)
                .toMutableList()
            playedTopics.forEach { rawTopic ->
                val topic = rawTopic.trim()
                if (topic.isEmpty() || topic.equals("Unknown", ignoreCase = true)) return@forEach
                updated.removeAll { it.equals(topic, ignoreCase = true) }
                updated.add(0, topic)
            }
            return updated.take(RECENT_TOPIC_LIMIT)
        }

        private fun appendRecentOutcomes(
            existing: String,
            outcomes: List<Boolean>,
        ): String = buildString {
            append(existing.filter { it == '0' || it == '1' })
            outcomes.forEach { append(if (it) '1' else '0') }
        }.takeLast(RECENT_TOPIC_OUTCOME_LIMIT)

        /**
         * Epoch-day in the DEVICE's timezone, so "a new day" starts at local
         * midnight instead of UTC midnight (which was 05:45 in Nepal).
         * Used by streaks AND the Daily Challenge — always in sync.
         * One-time transition note: previously-stored UTC days differ by at
         * most 1, so an existing streak survives (it may count one bonus day
         * across the switch, never resets).
         */
        fun localEpochDay(): Long {
            val now = System.currentTimeMillis()
            return (now + java.util.TimeZone.getDefault().getOffset(now)) / DAY_MS
        }
    }
}
