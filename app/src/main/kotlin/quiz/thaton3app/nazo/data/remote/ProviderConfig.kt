package quiz.thaton3app.nazo.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight description of a single model offered by a provider. `id` is what we send to the API;
 * `name`/`description` are for display; `isFree` lets the UI surface $0 models (e.g. OpenRouter).
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val isFree: Boolean = false,
)

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
    val models: List<ModelInfo>,
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

    /**
     * Model-list endpoint for this provider, or null when it can't be queried. Gemini puts the key
     * in the URL; OpenAI-style providers (including OpenRouter) expose a `/v1/models` (or
     * `/api/v1/models`) endpoint behind a Bearer key.
     */
    fun modelsUrl(apiKey: String): String? = when (kind) {
        ProviderKind.GEMINI -> "https://$host/v1beta/models?key=$apiKey"
        ProviderKind.OPENAI -> when (id) {
            "openrouter" -> "https://openrouter.ai/api/v1/models"
            else -> "https://$host/v1/models"
        }
        ProviderKind.ANTHROPIC -> null
    }

    /**
     * Parses a raw model-list response into [ModelInfo]s using the provider's shape. Gemini returns
     * `models[]` with `name` + `supportedGenerationMethods`; OpenAI-style providers return `data[]`
     * with an `id`. OpenRouter's entries additionally carry `name`, `description` and `pricing`
     * (prompt/completion as strings, "0" meaning free), which we use for the free-model filter.
     */
    fun parseModels(raw: String): List<ModelInfo> = when (kind) {
        ProviderKind.GEMINI -> {
            val models = JSONObject(raw).optJSONArray("models") ?: JSONArray()
            val list = mutableListOf<ModelInfo>()
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val methods = m.optJSONArray("supportedGenerationMethods")
                val canGenerate = methods != null && (0 until methods.length())
                    .any { methods.getString(it).contains("generateContent", ignoreCase = true) }
                if (canGenerate) {
                    val id = m.optString("name", "").substringAfterLast("/")
                    if (id.startsWith("gemini", ignoreCase = true)) list += ModelInfo(id, id)
                }
            }
            list
        }
        ProviderKind.OPENAI -> {
            val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<ModelInfo>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                when (id) {
                    "openrouter" -> {
                        val pricing = m.optJSONObject("pricing")
                        val prompt = pricing?.optString("prompt", "0") ?: "0"
                        val completion = pricing?.optString("completion", "0") ?: "0"
                        val isFree = prompt == "0" && completion == "0"
                        val mid = m.optString("id", "")
                        if (mid.isNotBlank()) {
                            val name = m.optString("name", "").ifBlank { mid }
                            list += ModelInfo(mid, name, m.optString("description", ""), isFree)
                        }
                    }
                    else -> {
                        val mid = m.optString("id", "")
                        if (mid.isNotBlank()) list += ModelInfo(mid, mid)
                    }
                }
            }
            list
        }
        ProviderKind.ANTHROPIC -> emptyList()
    }

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
        models = emptyList(),
    ),
    ProviderEndpoint(
        id = "openrouter",
        kind = ProviderKind.OPENAI,
        host = "openrouter.ai",
        path = "/api/v1/chat/completions",
        urlKey = false,
        models = emptyList(),
    ),
)

fun providerById(id: String): ProviderEndpoint? = PROVIDERS.firstOrNull { it.id == id }
