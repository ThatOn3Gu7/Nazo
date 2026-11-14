package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
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
     * expired streak and a damaged stats value have to be caught.
     *
     * A malformed preference can otherwise crash any of those entry points.
     * Only the failures that mean "this value is not a QuizStats document" are
     * recovered here; unrelated storage/runtime failures still surface. The
     * bad value is replaced with a valid empty document so every later reader
     * sees the same safe state.
     *
     * Missing a day is a non-event: no code runs when the user simply does not
     * play, so a streak broken by an inactive day stayed on disk (and on
     * screen) until the next quiz was recorded. Folding
     * [QuizStats.withStreakExpiry] into the read makes the reset happen the
     * first time anything looks at the stats on the new day.
     */
    fun get(): QuizStats {
        val raw = try {
            prefs.getString(KEY, null)
        } catch (_: ClassCastException) {
            return resetCorruptStats()
        }
        if (raw == null) return QuizStats()

        val stored = try {
            QuizStatsJson.decode(raw)
        } catch (_: JSONException) {
            return resetCorruptStats()
        } catch (_: IllegalArgumentException) {
            return resetCorruptStats()
        }

        val current = stored.withStreakExpiry()
        if (current != stored) save(current)
        return current
    }

    private fun resetCorruptStats(): QuizStats = QuizStats().also(::save)

    fun save(stats: QuizStats) {
        prefs.edit().putString(KEY, QuizStatsJson.encode(stats)).apply()
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

    internal companion object {
        const val KEY = "quiz_stats_v1"
    }
}

/**
 * Strict wire-format codec shared by local reads and backup validation.
 *
 * Older documents may omit fields (they retain the historical zero/empty
 * defaults), but a field that is present must have the right JSON shape and the
 * resulting counters must describe a possible aggregate. In particular, JSON
 * strings/fractions are not silently coerced into integers.
 */
internal object QuizStatsJson {
    private const val MAX_REASONABLE_EPOCH_DAY = 3_652_500L // safely beyond year 9999

    fun encode(stats: QuizStats): String = JSONObject().apply {
        put("totalQuizzes", stats.totalQuizzes)
        put("totalQuestionsAnswered", stats.totalQuestionsAnswered)
        put("totalCorrect", stats.totalCorrect)
        put("bestCorrectCombo", stats.bestCorrectCombo)
        put("currentStreakDays", stats.currentStreakDays)
        put("bestStreakDays", stats.bestStreakDays)
        put("lastQuizEpochDay", stats.lastQuizEpochDay ?: JSONObject.NULL)
        put("difficultyPlays", JSONObject(stats.difficultyPlays))
        put("difficultyCorrect", JSONObject(stats.difficultyCorrect))
        put("difficultyAnswered", JSONObject(stats.difficultyAnswered))
        put("animeAnswered", JSONObject(stats.animeAnswered))
        put("animeCorrect", JSONObject(stats.animeCorrect))
    }.toString()

    fun decode(json: String): QuizStats {
        require(json.isNotBlank()) { "Quiz statistics are blank" }
        val o = JSONObject(json)

        val stats = QuizStats(
            totalQuizzes = intField(o, "totalQuizzes"),
            totalQuestionsAnswered = intField(o, "totalQuestionsAnswered"),
            totalCorrect = intField(o, "totalCorrect"),
            bestCorrectCombo = intField(o, "bestCorrectCombo"),
            currentStreakDays = intField(o, "currentStreakDays"),
            bestStreakDays = intField(o, "bestStreakDays"),
            lastQuizEpochDay = nullableLongField(o, "lastQuizEpochDay"),
            difficultyPlays = intMap(o, "difficultyPlays"),
            difficultyCorrect = intMap(o, "difficultyCorrect"),
            difficultyAnswered = intMap(o, "difficultyAnswered"),
            animeAnswered = intMap(o, "animeAnswered"),
            animeCorrect = intMap(o, "animeCorrect"),
        )
        validate(stats)
        return stats
    }

    private fun intField(o: JSONObject, key: String): Int {
        if (!o.has(key)) return 0
        val value = wholeLong(o.get(key), key)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            "Quiz statistics field '$key' is outside the integer range"
        }
        return value.toInt()
    }

    private fun nullableLongField(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        return wholeLong(o.get(key), key)
    }

    private fun wholeLong(value: Any?, field: String): Long {
        val text = when (value) {
            is Byte, is Short, is Int, is Long -> value.toString()
            else -> throw IllegalArgumentException(
                "Quiz statistics field '$field' must be a whole number"
            )
        }
        return text.toLongOrNull()
            ?: throw IllegalArgumentException("Quiz statistics field '$field' is not a valid number")
    }

    private fun intMap(o: JSONObject, key: String): Map<String, Int> {
        if (!o.has(key)) return emptyMap()
        val mapObject = o.get(key) as? JSONObject
            ?: throw IllegalArgumentException("Quiz statistics field '$key' must be an object")
        val result = LinkedHashMap<String, Int>()
        for (name in mapObject.keys().asSequence().toList()) {
            require(name.isNotBlank()) { "Quiz statistics map '$key' contains a blank name" }
            val value = wholeLong(mapObject.get(name), "$key.$name")
            require(value in 0L..Int.MAX_VALUE.toLong()) {
                "Quiz statistics map '$key' contains an invalid count"
            }
            result[name] = value.toInt()
        }
        return result
    }

    private fun validate(stats: QuizStats) {
        val scalarCounts = listOf(
            stats.totalQuizzes,
            stats.totalQuestionsAnswered,
            stats.totalCorrect,
            stats.bestCorrectCombo,
            stats.currentStreakDays,
            stats.bestStreakDays,
        )
        require(scalarCounts.all { it >= 0 }) { "Quiz statistics contain a negative count" }
        require(stats.totalCorrect <= stats.totalQuestionsAnswered) {
            "Correct answers exceed answered questions"
        }
        require(stats.bestCorrectCombo <= stats.totalCorrect) {
            "Best combo exceeds total correct answers"
        }
        require(stats.currentStreakDays <= stats.bestStreakDays) {
            "Current streak exceeds best streak"
        }
        require(stats.currentStreakDays <= stats.totalQuizzes && stats.bestStreakDays <= stats.totalQuizzes) {
            "Streak exceeds quizzes played"
        }
        require(stats.currentStreakDays == 0 || stats.lastQuizEpochDay != null) {
            "A current streak requires a last quiz day"
        }
        stats.lastQuizEpochDay?.let { day ->
            require(day in 0L..MAX_REASONABLE_EPOCH_DAY) { "Invalid last quiz epoch day" }
        }

        require(stats.difficultyPlays.values.sumAsLong() <= stats.totalQuizzes.toLong()) {
            "Difficulty plays exceed quizzes played"
        }
        validateBreakdown(
            correct = stats.difficultyCorrect,
            answered = stats.difficultyAnswered,
            totalCorrect = stats.totalCorrect,
            totalAnswered = stats.totalQuestionsAnswered,
            label = "Difficulty",
        )
        validateBreakdown(
            correct = stats.animeCorrect,
            answered = stats.animeAnswered,
            totalCorrect = stats.totalCorrect,
            totalAnswered = stats.totalQuestionsAnswered,
            label = "Anime",
        )
    }

    private fun validateBreakdown(
        correct: Map<String, Int>,
        answered: Map<String, Int>,
        totalCorrect: Int,
        totalAnswered: Int,
        label: String,
    ) {
        require(answered.values.sumAsLong() <= totalAnswered.toLong()) {
            "$label answered counts exceed the total"
        }
        require(correct.values.sumAsLong() <= totalCorrect.toLong()) {
            "$label correct counts exceed the total"
        }
        require(correct.all { (name, count) -> count <= answered.getOrDefault(name, 0) }) {
            "$label correct counts exceed answered counts"
        }
    }

    private fun Collection<Int>.sumAsLong(): Long = fold(0L) { total, value -> total + value }
}
