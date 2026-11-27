package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** In-app text-size choices; the multiplier is applied on top of Android's font scale. */
enum class AccessibilityTextScale(
    val id: String,
    val label: String,
    val description: String,
    val multiplier: Float,
) {
    STANDARD("standard", "Standard", "Use Android's selected text size", 1f),
    LARGE("large", "Large", "15% larger than your Android text size", 1.15f),
    EXTRA_LARGE("extra_large", "Extra large", "30% larger than your Android text size", 1.30f),
    ;

    companion object {
        fun fromId(id: String?): AccessibilityTextScale =
            values().firstOrNull { it.id == id } ?: STANDARD
    }
}

/** Extra countdown time applied consistently to every player-facing game clock. */
enum class TimerAssist(
    val id: String,
    val label: String,
    val description: String,
    val multiplier: Float,
) {
    STANDARD("standard", "Standard time", "Use each mode's original countdown", 1f),
    RELAXED("relaxed", "More time", "50% longer question and Guessing clocks", 1.5f),
    EXTENDED("extended", "Double time", "Twice as long for question and Guessing clocks", 2f),
    ;

    companion object {
        fun fromId(id: String?): TimerAssist =
            values().firstOrNull { it.id == id } ?: STANDARD
    }
}

/** Pure accessibility rules shared by storage, theme, and gameplay. */
object AccessibilityPolicy {
    fun reduceMotion(systemRequestsReducedMotion: Boolean, appRequestsReducedMotion: Boolean): Boolean =
        systemRequestsReducedMotion || appRequestsReducedMotion

    fun seconds(baseSeconds: Int, assist: TimerAssist): Int {
        require(baseSeconds > 0) { "Timer duration must be positive" }
        return (baseSeconds * assist.multiplier).roundToInt().coerceAtLeast(1)
    }

    fun millis(baseMillis: Long, assist: TimerAssist): Long {
        require(baseMillis > 0L) { "Timer duration must be positive" }
        return (baseMillis * assist.multiplier).roundToLong().coerceAtLeast(1L)
    }
}

/** Durable accessibility choices. Invalid imported values safely fall back to defaults. */
class AccessibilityPreferences internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    var reduceMotion: Boolean
        get() = safeBoolean(KEY_REDUCE_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()

    var highContrast: Boolean
        get() = safeBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    var textScale: AccessibilityTextScale
        get() = AccessibilityTextScale.fromId(safeString(KEY_TEXT_SCALE))
        set(value) = prefs.edit().putString(KEY_TEXT_SCALE, value.id).apply()

    var timerAssist: TimerAssist
        get() = TimerAssist.fromId(safeString(KEY_TIMER_ASSIST))
        set(value) = prefs.edit().putString(KEY_TIMER_ASSIST, value.id).apply()

    /** Haptics predate this screen, so true preserves the established default. */
    var hapticsEnabled: Boolean
        get() = safeBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    private fun safeBoolean(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (_: ClassCastException) {
        prefs.edit().remove(key).apply()
        default
    }

    private fun safeString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (_: ClassCastException) {
        prefs.edit().remove(key).apply()
        null
    }

    internal companion object {
        const val PREFS_NAME = "nazo_accessibility"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_TEXT_SCALE = "text_scale"
        const val KEY_TIMER_ASSIST = "timer_assist"
        const val KEY_HAPTICS = "haptics_enabled"
    }
}
