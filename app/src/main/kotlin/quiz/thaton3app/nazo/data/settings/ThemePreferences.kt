package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-sensitive UI preferences: selected theme mode ("system" | "light" | "dark")
 * and accent name ("mint" | "rose" | "indigo" | "bronze" | "slate").
 */
class ThemePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_theme", Context.MODE_PRIVATE)

    var mode: String
        get() = prefs.getString(KEY_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var accent: String
        get() = prefs.getString(KEY_ACCENT, "mint") ?: "mint"
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "theme_accent"
    }
}
