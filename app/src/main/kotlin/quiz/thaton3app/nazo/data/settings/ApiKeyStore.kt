package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import quiz.thaton3app.nazo.data.remote.ProviderConfig

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
     * generation error screen). Falls back to the provider's static default list. */
    fun getModels(providerId: String): List<String> {
        val raw = prefs.getString(modelsFor(providerId), null)
        if (!raw.isNullOrBlank()) {
            return raw.split("|").filter { it.isNotBlank() }
        }
        return ProviderConfig.providerById(providerId)?.models ?: emptyList()
    }

    fun saveModels(providerId: String, models: List<String>) {
        prefs.edit().putString(modelsFor(providerId), models.joinToString("|")).apply()
    }

    /** First provider id (in [PROVIDER_ORDER]) that has a non-blank stored key, or null. */
    fun getActiveProvider(): String? =
        PROVIDER_ORDER.firstOrNull { !getKey(it).isNullOrBlank() }

    fun hasAnyActiveKey(): Boolean = getActiveProvider() != null

    private fun keyFor(id: String) = "key_$id"
    private fun modelFor(id: String) = "model_$id"
    private fun modelsFor(id: String) = "models_$id"

    companion object {
        val PROVIDER_ORDER = listOf(
            "gemini",
            "chatgpt",
            "openrouter",
            "deepseek",
            "mistral",
            "claude",
        )
    }
}
