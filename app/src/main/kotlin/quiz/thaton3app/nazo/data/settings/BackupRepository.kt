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
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to open backup file")
        applyJson(context, bytes.toString(Charsets.UTF_8))
    }

    suspend fun importFromPath(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) throw IllegalStateException("No auto-backup file found")
        applyJson(context, file.readText(Charsets.UTF_8))
    }

    private fun applyJson(context: Context, json: String) {
        val root = JSONObject(json)
        val stores = root.getJSONObject("stores")
        for (name in stores.keys().asSequence().toList()) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val map = stores.getJSONObject(name)
            val edit: SharedPreferences.Editor = prefs.edit()
            edit.clear()
            for (k in map.keys().asSequence().toList()) {
                val tv = map.getJSONObject(k)
                when (tv.getString("t")) {
                    "string" -> edit.putString(k, tv.getString("v"))
                    "bool" -> edit.putBoolean(k, tv.getBoolean("v"))
                    "int" -> edit.putInt(k, tv.getInt("v"))
                    "long" -> edit.putLong(k, tv.getLong("v"))
                    "float" -> edit.putFloat(k, tv.getDouble("v").toFloat())
                    "string_set" -> {
                        val arr = tv.getJSONArray("v")
                        val set = LinkedHashSet<String>()
                        for (i in 0 until arr.length()) set.add(arr.getString(i))
                        edit.putStringSet(k, set)
                    }
                }
            }
            edit.apply()
        }
    }
}
