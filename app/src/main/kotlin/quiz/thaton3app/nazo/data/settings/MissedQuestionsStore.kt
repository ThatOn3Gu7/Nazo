package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.data.Question

/**
 * The "practice deck": every quiz question the player got WRONG, persisted as
 * full Question objects so a practice run can replay them offline at any
 * time. Capped FIFO at [MAX_MISSED]; answering a stored question CORRECTLY
 * anywhere (normal quiz, daily, practice run…) removes it — the deck always
 * reflects what the player still hasn't mastered.
 *
 * Own prefs file ("nazo_missed", part of the backup set). Loaded once,
 * kept in memory, persisted on every mutation.
 */
class MissedQuestionsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_missed", Context.MODE_PRIVATE)

    private val questions: MutableList<Question> = run {
        val raw = prefs.getString(KEY_QUESTIONS, null)
        if (raw == null) mutableListOf() else runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Question(
                    anime = o.optString("anime"),
                    theme = o.optString("theme"),
                    difficulty = o.optString("difficulty", "Medium"),
                    text = o.optString("text"),
                    options = o.optJSONArray("options")?.let { opts ->
                        List(opts.length()) { j -> opts.getString(j) }
                    } ?: emptyList(),
                    correctAnswer = o.optString("correctAnswer"),
                    explanation = o.optString("explanation"),
                )
            }
        }.getOrDefault(mutableListOf())
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun save() {
        val arr = JSONArray()
        questions.forEach { q ->
            arr.put(
                JSONObject()
                    .put("anime", q.anime)
                    .put("theme", q.theme)
                    .put("difficulty", q.difficulty)
                    .put("text", q.text)
                    .put("options", JSONArray().also { a -> q.options.forEach { a.put(it) } })
                    .put("correctAnswer", q.correctAnswer)
                    .put("explanation", q.explanation)
            )
        }
        prefs.edit().putString(KEY_QUESTIONS, arr.toString()).apply()
    }

    /** Adds a question the player just missed (deduped by text, FIFO cap). */
    @Synchronized
    fun recordMiss(question: Question) {
        if (question.text.isBlank() || question.options.isEmpty()) return
        val norm = normalize(question.text)
        questions.removeAll { normalize(it.text) == norm }
        questions.add(question)
        while (questions.size > MAX_MISSED) questions.removeAt(0)
        save()
    }

    /** The player finally got it right — drop it from the deck. */
    @Synchronized
    fun recordCorrect(questionText: String) {
        val norm = normalize(questionText)
        if (questions.removeAll { normalize(it.text) == norm }) save()
    }

    @Synchronized
    fun count(): Int = questions.size

    /** Up to [max] missed questions, oldest misses first, for a practice run. */
    @Synchronized
    fun practiceSet(max: Int = 10): List<Question> =
        questions.take(max).map { it.withShuffledOptions() }

    private companion object {
        const val KEY_QUESTIONS = "questions"
        const val MAX_MISSED = 100
    }
}
