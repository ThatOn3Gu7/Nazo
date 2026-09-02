package quiz.thaton3app.nazo.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object UpdateDownloader {
    private const val PREFS = "nazo_update_download"
    private const val KEY_ID = "download_id"
    private const val APK_NAME = "nazo-update.apk"
    private const val MIN_APK_BYTES = 1024L * 1024L

    /**
     * In-app streaming download with live progress. Runs on IO, reports
     * (downloadedBytes, totalBytes) roughly every 100ms — totalBytes is -1
     * when the server doesn't send Content-Length. Cooperatively cancellable:
     * cancel the calling coroutine and the partial file is cleaned up.
     * Returns the finished APK file or throws.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val file = File(context.getExternalFilesDir(null), APK_NAME)
        file.delete()
        var conn: HttpURLConnection? = null
        try {
            // GitHub asset URLs redirect to a CDN; HttpURLConnection follows
            // same-protocol redirects itself, this loop covers the rest.
            var currentUrl = url
            var redirects = 0
            while (true) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = conn!!.responseCode
                if (code in 301..308) {
                    val loc = conn!!.getHeaderField("Location")
                        ?: throw IOException("Redirect without location")
                    conn!!.disconnect()
                    currentUrl = loc
                    if (++redirects > 5) throw IOException("Too many redirects")
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
                break
            }
            val total = conn!!.contentLengthLong
            conn!!.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        coroutineContext.ensureActive() // cancellation point
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 100) {
                            lastEmit = now
                            onProgress(downloaded, total)
                        }
                    }
                    onProgress(downloaded, total) // final 100% tick
                }
            }
            if (file.length() < MIN_APK_BYTES) {
                throw IOException("Downloaded file looks incomplete")
            }
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            conn?.disconnect()
        }
    }

    fun enqueue(context: Context, url: String): Boolean = try {
        File(context.getExternalFilesDir(null), APK_NAME)?.delete()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Nazo update")
            setDescription("Downloading new version...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, null, APK_NAME)
            setMimeType("application/vnd.android.package-archive")
        }
        val id = dm.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_ID, id).apply()
        true
    } catch (_: Exception) {
        false
    }

    fun install(context: Context): Boolean {
        val file = File(context.getExternalFilesDir(null), APK_NAME)
        if (!file.exists() || file.length() < MIN_APK_BYTES) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun findApkFiles(context: Context): List<File> {
        val dir = context.getExternalFilesDir(null) ?: return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }
            ?.toList() ?: emptyList()
    }

    fun deleteApkFiles(files: List<File>): Int = files.count { it.delete() }
}

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val prefs = context.getSharedPreferences("nazo_update_download", Context.MODE_PRIVATE)
        if (id != prefs.getLong("download_id", -1L)) return
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var successful = false
        dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                successful = status == DownloadManager.STATUS_SUCCESSFUL
            }
        }
        if (successful) {
            if (UpdateDownloader.install(appContext)) {
                Toast.makeText(appContext, "Installing update...", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, "Download incomplete, please try again", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(appContext, "Update download failed", Toast.LENGTH_LONG).show()
        }
    }
}
