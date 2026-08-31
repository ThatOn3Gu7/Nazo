package quiz.thaton3app.nazo.data

/**
 * Aggregate quiz statistics, persisted as a single JSON blob (see [QuizStatsStore]).
 *
 * All mutations are pure: [record] returns a NEW instance with the quiz folded in,
 * so the store can read -> transform -> write without any mutable shared state.
 *
 * Tracked insights:
 *  - total quizzes, questions answered, correct answers (overall accuracy)
 *  - current / best daily streak (epoch-day based, no java.time needed)
 *  - per-difficulty play count + correct/answered (accuracy per difficulty)
 *  - per-anime answered/correct (drives "top mastered anime")
 */
data class QuizStats(
    val totalQuizzes: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val totalCorrect: Int = 0,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastQuizEpochDay: Long? = null,
    val difficultyPlays: Map<String, Int> = emptyMap(),
    val difficultyCorrect: Map<String, Int> = emptyMap(),
    val difficultyAnswered: Map<String, Int> = emptyMap(),
    val animeAnswered: Map<String, Int> = emptyMap(),
    val animeCorrect: Map<String, Int> = emptyMap(),
) {
    fun record(
        difficulty: String,
        questions: List<Question>,
        userAnswers: List<String?>,
    ): QuizStats {
        val today = System.currentTimeMillis() / DAY_MS

        val newStreak = when {
            lastQuizEpochDay == today -> currentStreakDays          // already counted today
            lastQuizEpochDay == today - 1 -> currentStreakDays + 1  // consecutive day
            else -> 1                                               // streak reset / first ever
        }

        val dPlays = difficultyPlays.toMutableMap()
        val dCorrect = difficultyCorrect.toMutableMap()
        val dAnswered = difficultyAnswered.toMutableMap()
        val aAnswered = animeAnswered.toMutableMap()
        val aCorrect = animeCorrect.toMutableMap()

        dPlays[difficulty] = dPlays.getOrDefault(difficulty, 0) + 1

        var answered = totalQuestionsAnswered
        var correct = totalCorrect

        questions.forEachIndexed { i, q ->
            val ans = userAnswers.getOrNull(i)
            val isCorrect = ans != null && ans == q.correctAnswer
            answered++
            if (isCorrect) correct++
            dAnswered[difficulty] = dAnswered.getOrDefault(difficulty, 0) + 1
            if (isCorrect) dCorrect[difficulty] = dCorrect.getOrDefault(difficulty, 0) + 1
            val anime = q.anime.ifBlank { "Unknown" }
            aAnswered[anime] = aAnswered.getOrDefault(anime, 0) + 1
            if (isCorrect) aCorrect[anime] = aCorrect.getOrDefault(anime, 0) + 1
        }

        return copy(
            totalQuizzes = totalQuizzes + 1,
            totalQuestionsAnswered = answered,
            totalCorrect = correct,
            currentStreakDays = newStreak,
            bestStreakDays = maxOf(bestStreakDays, newStreak),
            lastQuizEpochDay = today,
            difficultyPlays = dPlays,
            difficultyCorrect = dCorrect,
            difficultyAnswered = dAnswered,
            animeAnswered = aAnswered,
            animeCorrect = aCorrect,
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
    ): QuizStats {
        val today = System.currentTimeMillis() / DAY_MS

        val newStreak = when {
            lastQuizEpochDay == today -> currentStreakDays          // already counted today
            lastQuizEpochDay == today - 1 -> currentStreakDays + 1  // consecutive day
            else -> 1                                               // streak reset / first ever
        }

        val dPlays = difficultyPlays.toMutableMap()
        val dCorrect = difficultyCorrect.toMutableMap()
        val dAnswered = difficultyAnswered.toMutableMap()
        val aAnswered = animeAnswered.toMutableMap()
        val aCorrect = animeCorrect.toMutableMap()

        dPlays[difficulty] = dPlays.getOrDefault(difficulty, 0) + 1
        dAnswered[difficulty] = dAnswered.getOrDefault(difficulty, 0) + answered
        dCorrect[difficulty] = dCorrect.getOrDefault(difficulty, 0) + correct
        val anime = topic.ifBlank { "Unknown" }
        aAnswered[anime] = aAnswered.getOrDefault(anime, 0) + answered
        aCorrect[anime] = aCorrect.getOrDefault(anime, 0) + correct

        return copy(
            totalQuizzes = totalQuizzes + 1,
            totalQuestionsAnswered = totalQuestionsAnswered + answered,
            totalCorrect = totalCorrect + correct,
            currentStreakDays = newStreak,
            bestStreakDays = maxOf(bestStreakDays, newStreak),
            lastQuizEpochDay = today,
            difficultyPlays = dPlays,
            difficultyCorrect = dCorrect,
            difficultyAnswered = dAnswered,
            animeAnswered = aAnswered,
            animeCorrect = aCorrect,
        )
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
