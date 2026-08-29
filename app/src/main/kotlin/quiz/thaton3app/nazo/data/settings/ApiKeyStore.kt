package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import quiz.thaton3app.nazo.data.remote.ModelInfo
import quiz.thaton3app.nazo.data.remote.providerById

/**
 * Persists per-provider API keys (securely via [SecureStorage]) and selected models
 * (non-sensitive, plain SharedPreferences), and derives which provider is "active"
 * (the first one that has a stored key).
 */
class ApiKeyStore(context: Context) {

    private val secure = SecureStorage(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_provider_models", Context.MODE_PRIVATE)

    fun getKey(providerId: String): String? = secure.get(keyFor(providerId))?.takeIf { it.isNotBlank() }

    fun saveKey(providerId: String, key: String) {
        secure.put(keyFor(providerId), key)
    }

    fun getModel(providerId: String): String? = prefs.getString(modelFor(providerId), null)

    fun saveModel(providerId: String, model: String) {
        prefs.edit().putString(modelFor(providerId), model).apply()
    }

    /** Last fetched list of models for this provider (so it can be offered on the
     * generation error screen). Stored as a JSON array of [ModelInfo]; legacy `|`-separated id
     * lists are still parsed for backwards compatibility. Falls back to the provider's static list. */
    fun getModels(providerId: String): List<ModelInfo> {
        val raw = prefs.getString(modelsFor(providerId), null)
        if (!raw.isNullOrBlank()) {
            if (raw.startsWith("[")) {
                return try {
                    val arr = JSONArray(raw)
                    val list = mutableListOf<ModelInfo>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list += ModelInfo(
                            id = o.optString("id", ""),
                            name = o.optString("name", ""),
                            description = o.optString("desc", ""),
                            isFree = o.optBoolean("free", false),
                        )
                    }
                    list.filter { it.id.isNotBlank() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            // Legacy format: pipe-separated ids, no metadata.
            return raw.split("|").filter { it.isNotBlank() }.map { ModelInfo(it, it) }
        }
        return providerById(providerId)?.models ?: emptyList()
    }

    fun saveModels(providerId: String, models: List<ModelInfo>) {
        val arr = JSONArray()
        models.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("name", it.name)
                    put("desc", it.description)
                    put("free", it.isFree)
                },
            )
        }
        prefs.edit().putString(modelsFor(providerId), arr.toString()).apply()
    }

    /** First provider id (in [PROVIDER_ORDER]) that has a non-blank stored key, or null. */
    fun getActiveProvider(): String? =
        PROVIDER_ORDER.firstOrNull { !getKey(it).isNullOrBlank() }

    fun hasAnyActiveKey(): Boolean = getActiveProvider() != null

    /** Provider id the user explicitly chose as active, or null when they haven't (falls back to auto). */
    fun getSelectedProvider(): String? {
        val sel = prefs.getString(selFor(), null)
        return if (sel != null && !getKey(sel).isNullOrBlank() && !getModel(sel).isNullOrBlank()) sel else null
    }

    fun saveSelectedProvider(id: String?) {
        prefs.edit().putString(selFor(), id).apply()
    }

    /** Providers that have both an API key and a selected model (i.e. ready to generate). */
    fun getConfiguredProviders(): List<String> =
        PROVIDER_ORDER.filter { !getKey(it).isNullOrBlank() && !getModel(it).isNullOrBlank() }

    private fun selFor() = "selected_provider"

    private fun keyFor(id: String) = "key_$id"
    private fun modelFor(id: String) = "model_$id"
    private fun modelsFor(id: String) = "models_$id"

    companion object {
        val PROVIDER_ORDER = listOf(
            "gemini",
            "chatgpt",
            "openrouter",
            "claude",
        )
    }
}
