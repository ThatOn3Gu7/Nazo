package quiz.thaton3app.nazo.data

import android.content.Context
import android.content.SharedPreferences

class UpdatePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_update_prefs", Context.MODE_PRIVATE)

    var updateFrequency: UpdateFrequency
        get() = try {
            UpdateFrequency.valueOf(
                prefs.getString("update_frequency", UpdateFrequency.WEEKLY.name)
                    ?: UpdateFrequency.WEEKLY.name
            )
        } catch (_: Exception) {
            UpdateFrequency.WEEKLY
        }
        set(value) = prefs.edit().putString("update_frequency", value.name).apply()

    var includePrereleases: Boolean
        get() = prefs.getBoolean("include_prereleases", false)
        set(value) = prefs.edit().putBoolean("include_prereleases", value).apply()

    val lastNotifiedVersion: String?
        get() = prefs.getString("last_notified_version", null)

    fun setLastNotifiedVersion(version: String) {
        prefs.edit().putString("last_notified_version", version).apply()
    }
}
