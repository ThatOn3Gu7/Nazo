package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * PERSISTENT question memory (the session-scoped twin lives in
 * session/SessionMemory). Remembers the last [MAX_HISTORY] quiz questions the
 * player has answered — across launches — so:
 *
 *  - AI providers get a "do not repeat these" list in the prompt that
 *    survives app restarts (SessionMemory alone forgets on relaunch);
 *  - the local bank prefers questions the player hasn't met recently.
 *
 * Matching is on normalized text (case/punctuation-insensitive), same rule as
 * SessionMemory, so cosmetic rewording still counts as a repeat. Stored as a
 * JSON array of original texts in its own prefs file ("nazo_qhistory", part
 * of the backup set). The list is loaded once and kept in memory; writes go
 * straight back to prefs.
 */
class QuestionHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_qhistory", Context.MODE_PRIVATE)

    private val texts: MutableList<String> = run {
        val raw = prefs.getString(KEY_TEXTS, null)
        if (raw == null) mutableListOf() else runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { arr.getString(it) }
        }.getOrDefault(mutableListOf())
    }
    private val normalized: MutableSet<String> =
        texts.mapTo(HashSet()) { normalize(it) }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun save() {
        val arr = JSONArray()
        texts.forEach { arr.put(it) }
        prefs.edit().putString(KEY_TEXTS, arr.toString()).apply()
    }

    /** Appends an answered question (deduped, FIFO-capped at [MAX_HISTORY]). */
    @Synchronized
    fun record(text: String) {
        if (text.isBlank()) return
        val norm = normalize(text)
        if (norm in normalized) texts.removeAll { normalize(it) == norm } // re-answered → moves to end
        normalized.add(norm)
        texts.add(text.trim())
        while (texts.size > MAX_HISTORY) {
            normalized.remove(normalize(texts.first()))
            texts.removeAt(0)
        }
        save()
    }

    /** True when this question was answered in the recent past (any launch). */
    @Synchronized
    fun isSeen(text: String): Boolean = normalize(text) in normalized

    /** Most recent entries for the provider prompt's avoid list. */
    @Synchronized
    fun recentForPrompt(max: Int = 20): List<String> = texts.takeLast(max)

    private companion object {
        const val KEY_TEXTS = "texts"
        const val MAX_HISTORY = 200
    }
}
