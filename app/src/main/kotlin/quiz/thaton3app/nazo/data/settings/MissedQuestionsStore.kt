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
 * Own prefs file ("nazo_missed", part of the backup set). Cached in memory
 * for the practice deck, persisted on every mutation, reloaded via [reload]
 * after a backup restore.
 */
class MissedQuestionsStore internal constructor(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("nazo_missed", Context.MODE_PRIVATE),
    )

    private val questions: MutableList<Question> = load()

    private fun load(): MutableList<Question> {
        val raw = prefs.getString(KEY_QUESTIONS, null)
        return if (raw == null) mutableListOf() else runCatching {
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

    /**
     * Re-reads prefs into the in-memory cache. Called after a backup restore:
     * without it the cached pre-restore list would be written back over the
     * restored practice deck by the next [recordMiss]/[recordCorrect].
     */
    @Synchronized
    fun reload() {
        questions.clear()
        questions.addAll(load())
    }

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

    /** Adds a question the player just missed (deduped by franchise + text, FIFO cap). */
    @Synchronized
    fun recordMiss(question: Question) {
        if (question.text.isBlank() || question.options.isEmpty()) return
        questions.removeAll { it.identity == question.identity }
        questions.add(question)
        while (questions.size > MAX_MISSED) questions.removeAt(0)
        save()
    }

    /** The player finally got it right — drop it from the deck. */
    @Synchronized
    fun recordCorrect(question: Question) {
        if (questions.removeAll { it.identity == question.identity }) save()
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
