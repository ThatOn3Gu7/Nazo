package quiz.thaton3app.nazo.modes.guessing_game

import org.json.JSONException
import org.json.JSONObject
import quiz.thaton3app.nazo.data.remote.ApiClient

/**
 * A single guessing-game round, straight from the AI payload. This is the exact
 * contract the Gemini / OpenRouter prompt wrapper requests:
 *
 * {
 *   "target_entity": "Character/Item Name",
 *   "aliases": ["Alias 1", "Alias 2"],
 *   "image_query": "Clean image search string",
 *   "easy_medium_options": ["Option 1", "Option 2", "Option 3", "Option 4"],
 *   "hard_autocomplete_pool": ["15 to 20 plausible names in topic"]
 * }
 */
data class GuessPayload(
    val targetEntity: String,
    val aliases: List<String> = emptyList(),
    val imageQuery: String = "",
    val easyMediumOptions: List<String> = emptyList(),
    val hardAutocompletePool: List<String> = emptyList(),
) {
    /** Every name that counts as a correct answer (target + aliases), normalized. */
    val acceptedAnswers: Set<String> by lazy {
        buildSet {
            add(normalizeName(targetEntity))
            aliases.forEach { add(normalizeName(it)) }
        }
            .filter { it.isNotBlank() }
            .toSet()
    }

    /** The name shown on reveal (falls back to the first alias). */
    fun displayAnswer(): String = targetEntity.ifBlank { aliases.firstOrNull() ?: "???" }

    /** Case / punctuation / spacing-insensitive check of a submitted answer. */
    fun isCorrect(submitted: String): Boolean {
        val n = normalizeName(submitted)
        return n.isNotBlank() && n in acceptedAnswers
    }

    /**
     * The four Easy/Medium choice buttons. The target is guaranteed to be present
     * (injected when the model forgot it) so the round is always solvable, and
     * short option lists are padded with plausible decoys from the auto-complete
     * pool. A decoy is never an accepted answer, so exactly one button wins.
     */
    val choiceOptions: List<String> by lazy {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun add(raw: String): Boolean {
            val n = normalizeName(raw)
            if (raw.isBlank() || n.isBlank() || n in seen) return false
            seen.add(n)
            out.add(raw)
            return true
        }
        val optionsHaveTarget = easyMediumOptions.any { normalizeName(it) in acceptedAnswers }
        if (optionsHaveTarget) {
            easyMediumOptions.forEach { add(it) }
        } else {
            add(displayAnswer())
            easyMediumOptions.forEach { add(it) }
        }
        for (filler in aliases + hardAutocompletePool) {
            if (out.size >= 4) break
            if (normalizeName(filler) in acceptedAnswers) continue // a decoy must never also be correct
            add(filler)
        }
        out.take(4)
    }

    /** Hard/Otaku auto-complete candidates: target + aliases + pool (deduped, stable order). */
    val suggestionPool: List<String> by lazy {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fun add(raw: String) {
            val n = normalizeName(raw)
            if (raw.isBlank() || n.isBlank() || n in seen) return
            seen.add(n)
            out.add(raw)
        }
        add(targetEntity)
        aliases.forEach { add(it) }
        hardAutocompletePool.forEach { add(it) }
        out
    }
}

/**
 * The outcome of one round (one shot). Kept apart from [GuessPayload] so the
 * results screen and the host's score only depend on the outcome, not the raw
 * AI payload.
 */
data class GuessRoundResult(
    val round: Int,
    val target: String,
    val aliases: List<String>,
    val imageQuery: String,
    val correct: Boolean,
    val answerText: String?,
    val points: Int,
    val remainingFraction: Float,
)

/** What the guessing game is currently doing; the host (NazoApp) owns this state. */
sealed interface GuessPhase {
    data object Idle : GuessPhase
    data class Preparing(val round: Int) : GuessPhase
    data class Playing(val payload: GuessPayload, val imageUrl: String?) : GuessPhase
    data class Error(val message: String, val isOffline: Boolean) : GuessPhase
}

/** Lowercase, letters/digits/spaces only — the comparison key for answers. */
fun normalizeName(raw: String): String =
    raw.trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Leniently parses the AI's JSON object into a [GuessPayload]. Throws
 * [IllegalStateException] on a structurally broken response so the caller can
 * surface a retryable error instead of an unplayable round.
 */
fun parseGuessPayload(raw: String): GuessPayload {
    // Shared coercion (see ApiClient): strips fences / <think> blocks, and if
    // the payload still isn't a bare object (prose around it, etc.), salvages
    // the first balanced {...} block from the text.
    val cleaned = ApiClient.coerceModelJson(raw)
    val o = try {
        JSONObject(cleaned)
    } catch (e: JSONException) {
        val block = ApiClient.firstBalancedBlock(cleaned, '{', '}') ?: throw e
        JSONObject(block)
    }
    val target = o.optString("target_entity", o.optString("targetEntity", "")).trim()
    if (target.isBlank()) {
        throw IllegalStateException("AI response was missing target_entity")
    }
    fun strList(key: String): List<String> =
        o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it, "") } } ?: emptyList()
    val options = strList("easy_medium_options").map { it.trim() }.filter { it.isNotBlank() }
    if (options.isEmpty()) {
        throw IllegalStateException("AI response had no easy/medium options")
    }
    return GuessPayload(
        targetEntity = target,
        aliases = strList("aliases").map { it.trim() }.filter { it.isNotBlank() },
        imageQuery = o.optString("image_query", o.optString("imageQuery", "")).trim().ifBlank { target },
        easyMediumOptions = options,
        hardAutocompletePool = strList("hard_autocomplete_pool").map { it.trim() }.filter { it.isNotBlank() },
    )
}
