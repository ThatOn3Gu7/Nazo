package quiz.thaton3app.nazo.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Describes how to talk to each supported LLM provider: the endpoint, how to
 * authenticate, the request body shape, and how to pull the generated text out of
 * the response.
 */
enum class ProviderKind { GEMINI, OPENAI, ANTHROPIC }

data class ProviderEndpoint(
    val id: String,
    val kind: ProviderKind,
    val host: String,
    val path: String,
    val urlKey: Boolean, // when true the API key goes in the URL query (Gemini)
    val models: List<String>,
) {
    fun buildUrl(model: String, apiKey: String): String {
        val base = "https://$host${path.replace("{model}", model)}"
        return if (urlKey) "$base?key=$apiKey" else base
    }

    fun headers(apiKey: String): Map<String, String> {
        val map = mutableMapOf("Content-Type" to "application/json")
        when (kind) {
            ProviderKind.GEMINI -> { /* key is in the URL */ }
            ProviderKind.OPENAI -> map["Authorization"] = "Bearer $apiKey"
            ProviderKind.ANTHROPIC -> {
                map["x-api-key"] = apiKey
                map["anthropic-version"] = "2023-06-01"
            }
        }
        return map
    }

    fun requestBody(prompt: String, model: String, systemPrompt: String = ""): String = when (kind) {
        ProviderKind.GEMINI -> JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
            }
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.9)
                    put("maxOutputTokens", 8192)
                    put("responseMimeType", "application/json")
                    put("responseSchema", questionSchema())
                },
            )
        }.toString()

        ProviderKind.OPENAI -> JSONObject().apply {
            val messages = JSONArray()
            if (systemPrompt.isNotBlank()) {
                messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            messages.put(JSONObject().put("role", "user").put("content", prompt))
            put("model", model)
            put("messages", messages)
            put("temperature", 0.7)
            put("response_format", JSONObject().put("type", "json_object"))
        }.toString()

        ProviderKind.ANTHROPIC -> JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
            )
        }.toString()
    }

    /** Gemini model-list endpoint (used to let the user pick from models they can access). Null for providers that don't support it. */
    fun modelsUrl(apiKey: String): String? =
        if (urlKey) "https://$host/v1beta/models?key=$apiKey" else null

    private fun questionSchema(): JSONObject = JSONObject().apply {
        put("type", "ARRAY")
        put(
            "items",
            JSONObject().apply {
                put("type", "OBJECT")
                put(
                    "properties",
                    JSONObject().apply {
                        put("theme", JSONObject().put("type", "STRING"))
                        put("question", JSONObject().put("type", "STRING"))
                        put(
                            "options",
                            JSONObject().apply {
                                put("type", "ARRAY")
                                put("items", JSONObject().put("type", "STRING"))
                                put("minItems", 4)
                                put("maxItems", 4)
                            },
                        )
                        put("correctAnswer", JSONObject().put("type", "STRING"))
                        put("explanation", JSONObject().put("type", "STRING"))
                    },
                )
                put(
                    "required",
                    JSONArray().apply {
                        put("theme"); put("question"); put("options"); put("correctAnswer"); put("explanation")
                    },
                )
                put(
                    "propertyOrdering",
                    JSONArray().apply {
                        put("theme"); put("question"); put("options"); put("correctAnswer"); put("explanation")
                    },
                )
            },
        )
    }
}

val PROVIDERS: List<ProviderEndpoint> = listOf(
    ProviderEndpoint(
        id = "gemini",
        kind = ProviderKind.GEMINI,
        host = "generativelanguage.googleapis.com",
        path = "/v1beta/models/{model}:generateContent",
        urlKey = true,
        models = listOf("gemini-2.5-flash", "gemini-1.5-pro"),
    ),
    ProviderEndpoint(
        id = "chatgpt",
        kind = ProviderKind.OPENAI,
        host = "api.openai.com",
        path = "/v1/chat/completions",
        urlKey = false,
        models = listOf("gpt-4o", "gpt-4o-mini"),
    ),
    ProviderEndpoint(
        id = "openrouter",
        kind = ProviderKind.OPENAI,
        host = "openrouter.ai",
        path = "/api/v1/chat/completions",
        urlKey = false,
        models = listOf("anthropic/claude-3.5-sonnet", "deepseek/deepseek-chat"),
    ),
    ProviderEndpoint(
        id = "deepseek",
        kind = ProviderKind.OPENAI,
        host = "api.deepseek.com",
        path = "/v1/chat/completions",
        urlKey = false,
        models = listOf("deepseek-chat", "deepseek-coder"),
    ),
    ProviderEndpoint(
        id = "mistral",
        kind = ProviderKind.OPENAI,
        host = "api.mistral.ai",
        path = "/v1/chat/completions",
        urlKey = false,
        models = listOf("mistral-large-latest", "mistral-small-latest"),
    ),
    ProviderEndpoint(
        id = "claude",
        kind = ProviderKind.ANTHROPIC,
        host = "api.anthropic.com",
        path = "/v1/messages",
        urlKey = false,
        models = listOf("claude-3-5-sonnet", "claude-3-haiku"),
    ),
)

fun providerById(id: String): ProviderEndpoint? = PROVIDERS.firstOrNull { it.id == id }
