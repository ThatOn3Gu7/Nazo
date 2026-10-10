package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import quiz.thaton3app.nazo.data.Question
import quiz.thaton3app.nazo.data.QuizStats

/**
 * Persists [QuizStats] as a single JSON string in SharedPreferences — consistent
 * with the project's other local stores (ThemePreferences / SecureStorage) and
 * using only the always-available [org.json] API (no extra dependencies).
 */
class QuizStatsStore internal constructor(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("nazo_stats", Context.MODE_PRIVATE),
    )

    /**
     * Every consumer of the stats — Home, Profile, Statistics, the widget and
     * the reminder scheduler — reads through here, so this is the one place an
     * expired streak has to be caught.
     *
     * Missing a day is a non-event: no code runs when the user simply does not
     * play, so a streak broken by an inactive day stayed on disk (and on
     * screen) until the next quiz was recorded. Folding
     * [QuizStats.withStreakExpiry] into the read makes the reset happen the
     * first time anything looks at the stats on the new day.
     *
     * The corrected value is written back when it changed, so the widget, the
     * notification scheduler and the UI can never disagree, and the work
     * happens once per expiry rather than on every read.
     */
    fun get(): QuizStats {
        val stored = QuizStats.fromJson(prefs.getString(KEY, null))
        val current = stored.withStreakExpiry()
        if (current != stored) save(current)
        return current
    }

    fun save(stats: QuizStats) {
        prefs.edit().putString(KEY, stats.toJson()).apply()
    }

    suspend fun record(
        difficulty: String,
        questions: List<Question>,
        userAnswers: List<String?>,
    ) {
        save(get().record(difficulty, questions, userAnswers))
    }

    /** Folds a completed guessing game into the same stats (see [QuizStats.recordGuessing]). */
    suspend fun recordGuessing(
        difficulty: String,
        topic: String,
        answered: Int,
        correct: Int,
    ) {
        save(get().recordGuessing(difficulty, topic, answered, correct))
    }

    private companion object {
        const val KEY = "quiz_stats_v1"
    }
}

private fun QuizStats.toJson(): String = JSONObject().apply {
    put("totalQuizzes", totalQuizzes)
    put("totalQuestionsAnswered", totalQuestionsAnswered)
    put("totalCorrect", totalCorrect)
    put("currentStreakDays", currentStreakDays)
    put("bestStreakDays", bestStreakDays)
    put("lastQuizEpochDay", lastQuizEpochDay ?: JSONObject.NULL)
    put("difficultyPlays", JSONObject(difficultyPlays))
    put("difficultyCorrect", JSONObject(difficultyCorrect))
    put("difficultyAnswered", JSONObject(difficultyAnswered))
    put("animeAnswered", JSONObject(animeAnswered))
    put("animeCorrect", JSONObject(animeCorrect))
}.toString()

private fun QuizStats.Companion.fromJson(json: String?): QuizStats {
    if (json.isNullOrBlank()) return QuizStats()
    val o = JSONObject(json)
    fun map(key: String): Map<String, Int> {
        val jo = o.optJSONObject(key) ?: return emptyMap()
        return jo.keys().asSequence().associateWith { jo.getInt(it) }
    }
    return QuizStats(
        totalQuizzes = o.optInt("totalQuizzes"),
        totalQuestionsAnswered = o.optInt("totalQuestionsAnswered"),
        totalCorrect = o.optInt("totalCorrect"),
        currentStreakDays = o.optInt("currentStreakDays"),
        bestStreakDays = o.optInt("bestStreakDays"),
        lastQuizEpochDay = if (o.has("lastQuizEpochDay") && !o.isNull("lastQuizEpochDay")) {
            o.getLong("lastQuizEpochDay")
        } else {
            null
        },
        difficultyPlays = map("difficultyPlays"),
        difficultyCorrect = map("difficultyCorrect"),
        difficultyAnswered = map("difficultyAnswered"),
        animeAnswered = map("animeAnswered"),
        animeCorrect = map("animeCorrect"),
    )
}
