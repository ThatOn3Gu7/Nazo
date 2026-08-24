package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny app-wide preference store. Currently holds only the manual "force offline"
 * toggle — useful for testing the local bank while the AI/online path isn't wired
 * up yet. Kept separate from ThemePreferences to avoid mixing concerns.
 */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_app_prefs", Context.MODE_PRIVATE)

    var forceOffline: Boolean
        get() = prefs.getBoolean(KEY_FORCE_OFFLINE, false)
        set(value) = prefs.edit().putBoolean(KEY_FORCE_OFFLINE, value).apply()

    companion object {
        private const val KEY_FORCE_OFFLINE = "force_offline"
    }
}
