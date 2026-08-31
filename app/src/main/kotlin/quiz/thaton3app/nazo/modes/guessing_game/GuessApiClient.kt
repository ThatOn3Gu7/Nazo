package quiz.thaton3app.nazo.modes.guessing_game

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.data.remote.ApiClient
import quiz.thaton3app.nazo.data.remote.providerById
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Network call for ONE guessing-game round. Reuses the existing fetcher
 * infrastructure in `data/remote` (provider endpoints, auth headers, request
 * body shape, response extraction, friendly HTTP errors) — only the prompt,
 * the JSON schema and the parse are guessing-specific, and they live here so
 * the mode stays self-contained.
 */
object GuessApiClient {

    private const val TAG = "NazoGuessApiClient"

    /**
     * The standard prompt wrapper: forces a single JSON object with exactly the
     * five guessing-game keys, identical for Gemini and OpenRouter.
     */
    const val GUESS_SYSTEM_PROMPT = """You are Nazo, an anime image-guessing game generator for a mobile quiz app.
Your job: pick ONE specific, well-known character, item, place, or ability from the requested topic and describe it for an image-guessing round.

Hard rules:
- Output ONLY a single JSON object. No markdown, no code fences, no commentary before or after.
- The object has exactly these keys:
  "target_entity" : the name of the single thing the player must guess (one character, item, place, or ability).
  "aliases"       : 0-3 widely-used alternative names / romanizations of the SAME entity (empty array if none).
  "image_query"   : a clean English image-search string that STARTS with the target entity's own full name,
                    followed by the franchise/show name (e.g. "Satoru Gojo Jujutsu Kaisen anime character").
                    It must identify the target itself — never a landmark, studio, product, or the franchise alone.
  "easy_medium_options" : an array of EXACTLY 4 distinct names from the same topic. The FIRST entry must be
                    "target_entity" itself, word for word. The other 3 are plausible decoys — never the target,
                    never any of its aliases, never blank.
  "hard_autocomplete_pool" : an array of 15-20 plausible names from the same topic (characters, items, places,
                    powers). It MUST include "target_entity" and every entry from "aliases". No duplicates.
- When the request gives an "avoid" list, the chosen target must NOT be any of those names.
- Prefer targets a real fan would recognize and an image search can actually find.
- Keep language consistent with the user's request (default English)."""

    /**
     * User prompt for one round. [avoidTargets] are the targets already played
     * this game, so the AI keeps picking something new.
     */
    fun buildGuessPrompt(topic: String, difficulty: String, avoidTargets: List<String>): String {
        val topicLine = if (topic.isNotBlank()) "\"$topic\"" else "any popular anime"
        val difficultyLine = if (difficulty.isNotBlank()) "\"$difficulty\"" else "\"Medium\""
        return buildString {
            append("Create a guessing-game round about $topicLine at $difficultyLine difficulty. ")
            append("For Easy and Medium, pick a target casual/regular fans would recognize. ")
            append("For Hard and Otaku Master, pick a deeper or more obscure target. ")
            if (avoidTargets.isNotEmpty()) {
                append("Avoid these targets — the player already saw them: ${avoidTargets.joinToString(", ")}. ")
            }
            append("Respond ONLY with the JSON object described in your instructions ")
            append("(keys: target_entity, aliases, image_query, easy_medium_options, hard_autocomplete_pool). ")
            append("Do not include any text outside the JSON object.")
        }
    }

    /** Gemini structured-output schema for the single-round payload object. */
    fun guessResponseSchema(): JSONObject = JSONObject().apply {
        fun strArray(minItems: Int, maxItems: Int): JSONObject = JSONObject().apply {
            put("type", "ARRAY")
            put("items", JSONObject().put("type", "STRING"))
            put("minItems", minItems)
            put("maxItems", maxItems)
        }
        put("type", "OBJECT")
        put(
            "properties",
            JSONObject().apply {
                put("target_entity", JSONObject().put("type", "STRING"))
                put("aliases", strArray(0, 3))
                put("image_query", JSONObject().put("type", "STRING"))
                put("easy_medium_options", strArray(4, 4))
                put("hard_autocomplete_pool", strArray(15, 20))
            },
        )
        put(
            "required",
            JSONArray().apply {
                put("target_entity"); put("aliases"); put("image_query")
                put("easy_medium_options"); put("hard_autocomplete_pool")
            },
        )
        put(
            "propertyOrdering",
            JSONArray().apply {
                put("target_entity"); put("aliases"); put("image_query")
                put("easy_medium_options"); put("hard_autocomplete_pool")
            },
        )
    }

    suspend fun generateGuessRound(
        providerId: String,
        apiKey: String,
        model: String,
        topic: String,
        difficulty: String,
        avoidTargets: List<String> = emptyList(),
    ): Result<GuessPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = providerById(providerId)
                ?: throw IllegalArgumentException("Unknown provider: $providerId")

            val url = endpoint.buildUrl(model, apiKey)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
                endpoint.headers(apiKey).forEach { (k, v) -> setRequestProperty(k, v) }
            }

            try {
                val body = endpoint.requestBody(
                    buildGuessPrompt(topic, difficulty, avoidTargets),
                    model,
                    GUESS_SYSTEM_PROMPT,
                    guessResponseSchema(),
                )
                connection.outputStream.use { os ->
                    os.write(body.toByteArray(StandardCharsets.UTF_8))
                }

                val code = connection.responseCode
                val raw = if (code in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                if (code !in 200..299) {
                    throw IOException(ApiClient.friendlyHttpError(code, endpoint.kind))
                }

                val content = ApiClient.extractContent(endpoint.kind, raw)
                parseGuessPayload(content)
            } finally {
                connection.disconnect()
            }
        }.onFailure { e -> Log.e(TAG, "generateGuessRound failed", e) }
    }
}
