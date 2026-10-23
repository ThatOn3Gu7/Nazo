package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup & restore preferences: a cached description of the last backup (manual
 * or automatic) and how often the auto-backup worker should run
 * ("off" | "daily" | "weekly").
 *
 * The last-backup details are stored as a small JSON blob written at the moment
 * the backup is created. Nothing here is ever recomputed by reopening the
 * backup file — a manual backup's SAF uri is not retained at all, and stat-ing
 * an old auto-backup on every screen open would be wasted I/O.
 *
 * This store is deliberately NOT in BackupRepository.STORES: a backup should not
 * carry a record of some earlier backup into a restored install.
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

    /**
     * Cached facts about the most recent successful backup, as measured when it
     * was written. Null until the first backup, or if the blob cannot be read.
     */
    data class LastBackup(
        val epoch: Long,
        val sizeBytes: Long,
        val records: Int,
        val categories: List<String>,
        val automatic: Boolean,
    )

    var lastBackup: LastBackup?
        get() {
            val raw = prefs.getString(KEY_LAST_DETAILS, null) ?: return null
            return runCatching {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("categories") ?: JSONArray()
                LastBackup(
                    epoch = o.getLong("epoch"),
                    sizeBytes = o.optLong("sizeBytes"),
                    records = o.optInt("records"),
                    categories = (0 until arr.length()).map { arr.optString(it) },
                    automatic = o.optBoolean("automatic"),
                )
            }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAST_DETAILS).apply()
                return
            }
            val o = JSONObject()
                .put("epoch", value.epoch)
                .put("sizeBytes", value.sizeBytes)
                .put("records", value.records)
                .put("automatic", value.automatic)
                .put("categories", JSONArray().apply { value.categories.forEach { put(it) } })
            // Keep the plain epoch in sync so nothing depends on the blob parsing.
            prefs.edit()
                .putString(KEY_LAST_DETAILS, o.toString())
                .putLong(KEY_LAST, value.epoch)
                .apply()
        }

    var autoBackupFrequency: String
        get() = prefs.getString(KEY_FREQ, "off") ?: "off"
        set(value) = prefs.edit().putString(KEY_FREQ, value).apply()

    private companion object {
        const val KEY_LAST = "last_backup_epoch"
        const val KEY_LAST_DETAILS = "last_backup_details"
        const val KEY_FREQ = "auto_backup_frequency"
    }
}
