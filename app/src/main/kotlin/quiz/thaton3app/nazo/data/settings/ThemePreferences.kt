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

    /** Which OS-theme variant of the launcher icon is currently applied: "light" | "dark" | "". */
    var appliedLauncherNight: String
        get() = prefs.getString(KEY_LAUNCHER_NIGHT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAUNCHER_NIGHT, value).apply()

    /** Whether the launcher icon should follow the device OS dark/light theme. */
    var iconFollowsOsTheme: Boolean
        get() = prefs.getBoolean(KEY_ICON_FOLLOWS_OS, true)
        set(value) = prefs.edit().putBoolean(KEY_ICON_FOLLOWS_OS, value).apply()

    /** Whether the bottom navigation bar floats as an elevated pill (true) or is a
     *  solid bar that covers the system gesture area (false). */
    var floatingNavBar: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_NAV, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_NAV, value).apply()

    /** Which game mode ("QUIZ" | "GUESSING") was last played/selected — pre-selected
     *  on the Home screen when the app launches. */
    var lastMode: String
        get() = prefs.getString(KEY_LAST_MODE, "QUIZ") ?: "QUIZ"
        set(value) = prefs.edit().putString(KEY_LAST_MODE, value).apply()

    /** Guessing-game image reveal style: "blur" or "pixel". */
    var guessRevealStyle: String
        get() = prefs.getString(KEY_GUESS_REVEAL_STYLE, "pixel") ?: "pixel"
        set(value) = prefs.edit().putString(KEY_GUESS_REVEAL_STYLE, value).apply()

    /** Whether the Guessing Game auto-crops each round's mystery image to the
     *  character's face + upper body (vision/PortraitCrop). When off, the
     *  original (un-cropped) image is shown as fetched. */
    var guessAutoCrop: Boolean
        get() = prefs.getBoolean(KEY_GUESS_AUTO_CROP, true)
        set(value) = prefs.edit().putBoolean(KEY_GUESS_AUTO_CROP, value).apply()

    /** Ambient background style variant ("shapes" | "constellation" | "rain" | "orbs"). */
    var backgroundStyle: String
        get() = prefs.getString(KEY_BG_STYLE, "shapes") ?: "shapes"
        set(value) = prefs.edit().putString(KEY_BG_STYLE, value).apply()

    /** End-of-game celebration ("graffiti") variant shown on the quiz / guessing
     *  completion screens: "none" | "burst" | "festive" | "rain" | "cannons" | "fireworks". */
    var celebrationStyle: String
        get() = prefs.getString(KEY_CELEBRATION_STYLE, "burst") ?: "burst"
        set(value) = prefs.edit().putString(KEY_CELEBRATION_STYLE, value).apply()

    /** Whether tapping anywhere in the app spawns interactive touch ripple bursts. */
    var touchRipples: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_RIPPLES, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_RIPPLES, value).apply()

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "theme_accent"
        const val KEY_LAUNCHER_NIGHT = "launcher_night"
        const val KEY_ICON_FOLLOWS_OS = "icon_follows_os"
        const val KEY_FLOATING_NAV = "floating_nav"
        const val KEY_LAST_MODE = "last_game_mode"
        const val KEY_GUESS_REVEAL_STYLE = "guess_reveal_style"
        const val KEY_GUESS_AUTO_CROP = "guess_auto_crop"
        const val KEY_BG_STYLE = "background_style"
        const val KEY_CELEBRATION_STYLE = "celebration_style"
        const val KEY_TOUCH_RIPPLES = "touch_ripples_enabled"
    }
}
