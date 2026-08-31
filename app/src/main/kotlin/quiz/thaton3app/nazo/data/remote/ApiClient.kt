package quiz.thaton3app.nazo.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.data.Question
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Performs the network call to a user-configured LLM provider and turns the response
 * into a list of [Question]. No third-party HTTP/JSON libraries are used — only the
 * Android SDK ([HttpURLConnection]) and [org.json], which are always available.
 */
object ApiClient {

    private const val TAG = "NazoApiClient"

    private const val GEMINI_SYSTEM_PROMPT = """You are Nazo, an expert anime trivia quiz generator for a mobile quiz app.
Your job: produce a set of multiple-choice anime trivia questions.

Hard rules:
- Output ONLY a JSON array. No markdown, no code fences, no commentary before or after.
- Produce exactly the number of questions requested.
- Each question object has exactly these keys:
  "theme"    : a short UPPERCASE sub-category tag (e.g. CHARACTERS, POWERS, STORY, WORLD, UNRESOLVED).
  "question" : the question text, clear and unambiguous.
  "options"  : an array of exactly 4 distinct, plausible strings.
  "correctAnswer" : the EXACT text of one of the 4 options (never "A"/"B"/an index).
  "explanation" : 1-2 sentences explaining the correct answer, factual, no major spoilers
                  for Easy/Medium (Hard/Otaku Master may include spoilers).
- Difficulty calibration:
  Easy        = casual fans; well-known facts.
  Medium      = regular viewers.
  Hard        = deep lore / specific moments.
  Otaku Master= obscure, niche, debated, or unresolved points.
- If the requested topic is unknown to you, still generate reasonable, clearly-flagged
  questions rather than refusing; prefer broadly accepted facts.
- Keep language consistent with the user's request (default English)."""

    suspend fun generateQuiz(
        providerId: String,
        apiKey: String,
        model: String,
        topic: String,
        difficulty: String,
        count: Int,
    ): Result<List<Question>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = providerById(providerId)
                ?: throw IllegalArgumentException("Unknown provider: $providerId")

            val prompt = buildUserPrompt(topic, difficulty, count)
            val url = endpoint.buildUrl(model, apiKey)

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
                endpoint.headers(apiKey).forEach { (k, v) -> setRequestProperty(k, v) }
            }

            try {
                val body = endpoint.requestBody(prompt, model, GEMINI_SYSTEM_PROMPT)
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
                    throw IOException(friendlyHttpError(code, endpoint.kind))
                }

                val content = extractContent(endpoint.kind, raw)
                val questions = parseQuestions(content, topic)
                if (questions.isEmpty()) {
                    throw IllegalStateException("Provider returned no questions")
                }
                questions
            } finally {
                connection.disconnect()
            }
        }.onFailure { e -> Log.e(TAG, "generateQuiz failed", e) }
    }

    // internal (not private) so other modes (e.g. modes/guessing_game) can reuse
    // the same response-shape handling instead of duplicating it.
    internal fun extractContent(kind: ProviderKind, raw: String): String = when (kind) {
        ProviderKind.GEMINI -> JSONObject(raw)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        ProviderKind.OPENAI -> JSONObject(raw)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")

        ProviderKind.ANTHROPIC -> JSONObject(raw)
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
    }

    private fun parseQuestions(raw: String, topic: String): List<Question> {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val arr = JSONArray(cleaned)
        val list = mutableListOf<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val options = o.optJSONArray("options")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            }?.filter { it.isNotBlank() } ?: emptyList()
            // Skip malformed entries so a single bad question can't break the whole quiz.
            if (options.isEmpty()) continue
            val rawQuestion = o.optString("question", o.optString("text", ""))
            if (rawQuestion.isBlank()) continue
            val questionText = rawQuestion
            val rawCorrect = o.optString("correctAnswer", "")
            val correctAnswer = if (rawCorrect in options) rawCorrect else options.first()
            list += Question(
                id = list.size + 1,
                anime = topic.ifBlank {
                    o.optString("anime", o.optString("theme", "Anime"))
                },
                theme = o.optString("theme", "ANIME QUIZ").uppercase(),
                text = questionText,
                options = options,
                correctAnswer = correctAnswer,
                explanation = o.optString("explanation", ""),
            )
        }
        return list
    }

    private fun buildUserPrompt(topic: String, difficulty: String, count: Int, language: String = "English"): String {
        val topicLine = if (topic.isNotBlank()) " about \"$topic\"" else " about any popular anime"
        val diffLine = if (difficulty.isNotBlank()) " at \"$difficulty\" difficulty" else ""
        return buildString {
            append("Generate $count anime trivia questions$topicLine$diffLine. ")
            append("Use exactly 4 answer options per question. ")
            append("Respond ONLY with the JSON array described in your instructions ")
            append("(keys: theme, question, options, correctAnswer, explanation). ")
            append("Do not include any text outside the JSON array.")
            if (language != "English") append(" Write all question text and explanations in $language.")
        }
    }

    /**
     * Fetches the list of models the user's key can actually use. For Gemini this queries the public
     * model list and keeps only those supporting `generateContent`. OpenAI-style providers (incl.
     * OpenRouter) hit their `/v1/models` endpoint behind a Bearer key. The response is parsed by
     * [ProviderEndpoint.parseModels], which also extracts OpenRouter's free/priced metadata.
     */
    suspend fun fetchModels(providerId: String, apiKey: String): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = providerById(providerId)
                ?: throw IllegalArgumentException("Unknown provider: $providerId")
            val url = endpoint.modelsUrl(apiKey)
            if (url == null) return@runCatching endpoint.models

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                endpoint.headers(apiKey).forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = connection.responseCode
                val raw = if (code in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                if (code !in 200..299) throw IOException("Models list returned HTTP $code")
                val models = endpoint.parseModels(raw)
                models.ifEmpty { endpoint.models }
            } finally {
                connection.disconnect()
            }
        }.onFailure { e -> Log.e(TAG, "fetchModels failed", e) }
    }

    // internal — shared with other modes' API clients (see extractContent above).
    internal fun friendlyHttpError(code: Int, kind: ProviderKind): String = when (code) {
        400 -> "Request error (check the selected model)."
        401, 403 -> "The API key was rejected. Check it in AI & Model Configuration."
        404 -> "That model wasn't found. Re-fetch models or pick another from the list."
        429 -> "Rate limit / quota reached. Try again later or use a local quiz."
        in 500..599 -> "The provider is temporarily unavailable. Try again."
        else -> "Provider returned HTTP $code."
    }
}

/** In-memory cache of generated quizzes for the current app session (keyed by request params). */
object QuizCache {
    private val map = LinkedHashMap<String, List<Question>>()
    private const val MAX = 20

    fun key(provider: String, model: String, topic: String, difficulty: String, count: Int): String =
        "$provider:$model:$topic:$difficulty:$count"

    fun get(key: String): List<Question>? = map[key]

    fun put(key: String, value: List<Question>) {
        map.remove(key)
        map[key] = value
        while (map.size > MAX) map.remove(map.keys.first())
    }
}
