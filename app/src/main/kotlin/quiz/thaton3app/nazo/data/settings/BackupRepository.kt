package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashSet

/**
 * Serializes the app's user-data SharedPreferences stores into a single portable
 * JSON bundle and writes it to either an SAF [Uri] (manual backup, user-chosen
 * location) or a fixed app-external file (automatic backup).
 *
 * Included stores: quiz stats, profile, theme, AI provider models, and the
 * AndroidKeystore-encrypted API-key blobs. The key blobs only re-decrypt on the
 * same device/keystore, which is the expected behaviour. Internal update-checker
 * stores are intentionally excluded (not user data).
 *
 * SharedPreferences values are type-tagged on export so they round-trip exactly
 * (Boolean / Int / Long / Float / String / Set<String>). A `content://` gallery
 * profile picture is skipped because that URI is not portable across devices —
 * the username and any emoji/remote-url picture are kept.
 */
object BackupRepository {

    private val STORES = listOf(
        "nazo_stats",
        "nazo_theme",
        "nazo_profile",
        "nazo_provider_models",
        "nazo_secure",
    )
    private const val PROFILE_PICTURE_KEY = "profile_picture_uri"

    fun autoBackupPath(context: Context): String =
        File(context.getExternalFilesDir(null), "Nazo/auto_backup.json").absolutePath

    suspend fun exportToUri(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(buildJson(context).toString().toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Unable to open backup destination")
    }

    suspend fun exportToPath(context: Context, path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(buildJson(context).toString(), Charsets.UTF_8)
    }

    private fun buildJson(context: Context): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())
        val stores = JSONObject()
        for (name in STORES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val map = JSONObject()
            for ((k, v) in prefs.all) {
                // Skip gallery content:// profile pictures — not portable.
                if (name == "nazo_profile" && k == PROFILE_PICTURE_KEY &&
                    v is String && v.startsWith("content://")
                ) {
                    continue
                }
                map.put(k, toTagged(v))
            }
            stores.put(name, map)
        }
        root.put("stores", stores)
        return root
    }

    private fun toTagged(v: Any?): JSONObject {
        val o = JSONObject()
        when (v) {
            is String -> {
                o.put("t", "string")
                o.put("v", v)
            }
            is Boolean -> {
                o.put("t", "bool")
                o.put("v", v)
            }
            is Int -> {
                o.put("t", "int")
                o.put("v", v)
            }
            is Long -> {
                o.put("t", "long")
                o.put("v", v)
            }
            is Float -> {
                o.put("t", "float")
                o.put("v", v)
            }
            is Set<*> -> {
                val arr = JSONArray()
                (v as Set<*>).forEach { if (it is String) arr.put(it) }
                o.put("t", "string_set")
                o.put("v", arr)
            }
            else -> {
                o.put("t", "string")
                o.put("v", v?.toString() ?: "")
            }
        }
        return o
    }

    suspend fun importFromUri(context: Context, uri: Uri) {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Unable to read backup file")
        applyValidated(context, parseAndValidate(content))
    }

    suspend fun importFromPath(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("No auto-backup file found")
        applyValidated(context, parseAndValidate(file.readText(Charsets.UTF_8)))
    }

    /** True only if the file is a well-formed Nazo backup bundle. */
    suspend fun validateUri(context: Context, uri: Uri): Boolean {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return false
            parseAndValidate(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun validatePath(context: Context, path: String): Boolean {
        return try {
            parseAndValidate(File(path).readText(Charsets.UTF_8))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parse and fully validate the backup JSON into memory. Throws if the file is
     * malformed or structurally invalid, so [applyValidated] only ever runs on a
     * verified bundle (no partial / corrupting restores).
     */
    private fun parseAndValidate(content: String): Map<String, Map<String, Pair<String, Any?>>> {
        val root = JSONObject(content)
        val stores = root.optJSONObject("stores")
            ?: throw IllegalArgumentException("Invalid backup file (missing 'stores')")
        val pending = LinkedHashMap<String, MutableMap<String, Pair<String, Any?>>>()
        for (name in stores.keys().asSequence().toList()) {
            val map = stores.optJSONObject(name)
                ?: throw IllegalArgumentException("Invalid backup file (store '$name')")
            val entry = LinkedHashMap<String, Pair<String, Any?>>()
            for (k in map.keys().asSequence().toList()) {
                val tv = map.optJSONObject(k)
                    ?: throw IllegalArgumentException("Invalid backup file (entry '$k')")
                val t = tv.optString("t")
                val v: Any? = when (t) {
                    "string" -> tv.optString("v", "")
                    "bool" -> tv.optBoolean("v")
                    "int" -> tv.optInt("v")
                    "long" -> tv.optLong("v")
                    "float" -> tv.optDouble("v").toFloat()
                    "string_set" -> {
                        val arr = tv.optJSONArray("v") ?: JSONArray()
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.optString(i))
                        set
                    }
                    else -> throw IllegalArgumentException("Invalid backup file (type '$t')")
                }
                entry[k] = t to v
            }
            pending[name] = entry
        }
        return pending
    }

    private fun applyValidated(context: Context, pending: Map<String, Map<String, Pair<String, Any?>>>) {
        for ((name, entry) in pending) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            edit.clear()
            for ((k, pair) in entry) {
                val (t, v) = pair
                when (t) {
                    "string" -> edit.putString(k, v as String)
                    "bool" -> edit.putBoolean(k, v as Boolean)
                    "int" -> edit.putInt(k, v as Int)
                    "long" -> edit.putLong(k, v as Long)
                    "float" -> edit.putFloat(k, v as Float)
                    "string_set" -> edit.putStringSet(k, v as Set<String>)
                }
            }
            edit.apply()
        }
    }
}
