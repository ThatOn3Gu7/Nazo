package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Backup & restore preferences: when the last backup was made, and how often the
 * auto-backup worker should run ("off" | "daily" | "weekly").
 */
class BackupPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_backup", Context.MODE_PRIVATE)

    var lastBackupEpoch: Long?
        get() = if (prefs.contains(KEY_LAST)) prefs.getLong(KEY_LAST, 0L) else null
        set(value) {
            if (value == null) prefs.edit().remove(KEY_LAST).apply()
            else prefs.edit().putLong(KEY_LAST, value).apply()
        }

    var autoBackupFrequency: String
        get() = prefs.getString(KEY_FREQ, "off") ?: "off"
        set(value) = prefs.edit().putString(KEY_FREQ, value).apply()

    private companion object {
        const val KEY_LAST = "last_backup_epoch"
        const val KEY_FREQ = "auto_backup_frequency"
    }
}
