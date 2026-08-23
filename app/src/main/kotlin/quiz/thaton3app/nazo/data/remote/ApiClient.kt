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

            val prompt = buildPrompt(topic, difficulty, count)
            val url = endpoint.buildUrl(model, apiKey)

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
                endpoint.headers(apiKey).forEach { (k, v) -> setRequestProperty(k, v) }
            }

            try {
                val body = endpoint.requestBody(prompt, model)
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
                    throw IOException("Provider returned HTTP $code: $raw")
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

    private fun extractContent(kind: ProviderKind, raw: String): String = when (kind) {
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
            val options = o.getJSONArray("options").let { a ->
                (0 until a.length()).map { a.getString(it) }
            }
            list += Question(
                id = i + 1,
                anime = o.optString("anime", if (topic.isNotBlank()) topic else o.optString("theme", "Anime")),
                theme = o.optString("theme", "ANIME QUIZ").uppercase(),
                text = o.optString("question", o.optString("text", "")),
                options = options,
                correctAnswer = o.optString("correctAnswer", options.firstOrNull() ?: ""),
                explanation = o.optString("explanation", ""),
            )
        }
        return list
    }

    private fun buildPrompt(topic: String, difficulty: String, count: Int): String {
        val topicLine = if (topic.isNotBlank()) " about the topic: \"$topic\"" else ""
        val diffLine = if (difficulty.isNotBlank()) " at \"$difficulty\" difficulty" else ""
        return buildString {
            append("You are an anime trivia quiz generator. Create $count multiple-choice ")
            append("questions$topicLine$diffLine. ")
            append("Respond with ONLY a JSON array and no other text or markdown. ")
            append("Each element must be an object with exactly these keys: ")
            append("\"theme\" (short uppercase category, e.g. \"JUJUTSU KAISEN\"), ")
            append("\"question\" (the question text), ")
            append("\"options\" (an array of exactly 4 distinct strings), ")
            append("\"correctAnswer\" (must exactly match one of the options), ")
            append("\"explanation\" (1-2 sentence explanation of the answer). ")
            append("Example element: ")
            append("{")
            append("\"theme\":\"NARUTO\",")
            append("\"question\":\"What is Naruto's signature jutsu?\",")
            append("\"options\":[\"Shadow Clone Jutsu\",\"Rasengan\",\"Chidori\",\"Byakugan\"],")
            append("\"correctAnswer\":\"Shadow Clone Jutsu\",")
            append("\"explanation\":\"The Shadow Clone Jutsu is Naruto's most used technique.\"")
            append("}.")
        }
    }
}
