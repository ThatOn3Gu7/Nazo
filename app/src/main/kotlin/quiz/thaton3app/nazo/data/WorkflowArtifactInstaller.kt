package quiz.thaton3app.nazo.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val WORKFLOW_ARCHIVE_NAME = "nazo-workflow-build.zip"
private const val WORKFLOW_APK_NAME = "nazo-workflow-build.apk"
private const val MIN_APK_BYTES = 1024L * 1024L
private const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
private const val MAX_APK_BYTES = 192L * 1024L * 1024L

/** Metadata read from a workflow APK after package, signature and version checks. */
data class VerifiedWorkflowApk(
    val file: File,
    val versionName: String,
    val versionCode: Long,
)

object WorkflowArtifactInstaller {
    fun apkFile(context: Context): File =
        File(context.getExternalFilesDir(null), WORKFLOW_APK_NAME)

    /** Downloads GitHub's artifact ZIP and extracts its one bounded APK. */
    suspend fun downloadApk(
        context: Context,
        artifact: WorkflowBuildArtifact,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (artifact.expired) throw IOException("This workflow artifact has expired")
        val directory = context.getExternalFilesDir(null)
            ?: throw IOException("App storage is unavailable")
        val archive = File(directory, WORKFLOW_ARCHIVE_NAME)
        val apk = File(directory, WORKFLOW_APK_NAME)
        archive.delete()
        apk.delete()
        try {
            downloadArchive(
                url = artifact.downloadUrl,
                destination = archive,
                expectedSize = artifact.sizeBytes,
                onProgress = onProgress,
            )
            extractSingleApk(archive, apk, MAX_APK_BYTES)
            if (apk.length() < MIN_APK_BYTES) {
                throw IOException("The extracted APK looks incomplete")
            }
            apk
        } catch (error: Exception) {
            apk.delete()
            throw error
        } finally {
            archive.delete()
        }
    }

    /**
     * Fails before Android's installer when a snapshot cannot replace this app.
     * The system installer remains authoritative and still asks for confirmation.
     */
    fun verify(context: Context, file: File): VerifiedWorkflowApk {
        if (!file.isFile || file.length() < MIN_APK_BYTES) {
            throw IOException("The workflow APK is missing or incomplete")
        }
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val candidate = packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw IOException("Android could not read the workflow APK")
        if (candidate.packageName != context.packageName) {
            throw IOException("The artifact is not a Nazo APK")
        }
        val installed = try {
            packageManager.getPackageInfo(context.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IOException("The installed Nazo package could not be inspected")
        }
        val installedCertificates = signingCertificateDigests(installed)
        val candidateCertificates = signingCertificateDigests(candidate)
        if (installedCertificates.isEmpty() || candidateCertificates.isEmpty() ||
            installedCertificates.intersect(candidateCertificates).isEmpty()
        ) {
            throw IOException("The artifact is not signed with this installation's certificate")
        }
        val installedVersion = PackageInfoCompat.getLongVersionCode(installed)
        val candidateVersion = PackageInfoCompat.getLongVersionCode(candidate)
        if (!workflowVersionCanReplace(installedVersion, candidateVersion)) {
            throw IOException(
                "Android blocks this version-code downgrade ($candidateVersion < $installedVersion)"
            )
        }
        return VerifiedWorkflowApk(
            file = file,
            versionName = candidate.versionName.orEmpty().ifBlank { "Unknown" },
            versionCode = candidateVersion,
        )
    }

    fun install(context: Context): Boolean =
        UpdateDownloader.installFile(context, apkFile(context))
}

private suspend fun downloadArchive(
    url: String,
    destination: File,
    expectedSize: Long,
    onProgress: (downloaded: Long, total: Long) -> Unit,
) {
    var connection: HttpURLConnection? = null
    var currentUrl = URL(url)
    var redirects = 0
    try {
        while (true) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Nazo-Android-Workflow-Installer")
                setRequestProperty("Accept", "application/octet-stream")
                if (currentUrl.host.equals("api.github.com", ignoreCase = true)) {
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            connection = conn
            val responseCode = conn.responseCode
            if (responseCode in 301..308) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("GitHub redirected without a download location")
                conn.disconnect()
                connection = null
                currentUrl = URL(currentUrl, location)
                redirects += 1
                if (redirects > 5) throw IOException("Too many artifact download redirects")
                continue
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val message = when (responseCode) {
                    HttpURLConnection.HTTP_UNAUTHORIZED ->
                        "This legacy Actions archive requires GitHub sign-in; refresh the catalog " +
                            "and choose a publicly mirrored snapshot"
                    HttpURLConnection.HTTP_GONE,
                    HttpURLConnection.HTTP_NOT_FOUND -> "This workflow snapshot is unavailable"
                    403 -> "GitHub refused the snapshot download or its API limit was reached"
                    else -> "Snapshot download failed with HTTP $responseCode"
                }
                throw IOException(message)
            }
            val headerSize = conn.contentLengthLong
            if (headerSize > MAX_ARCHIVE_BYTES || expectedSize > MAX_ARCHIVE_BYTES) {
                throw IOException("The workflow artifact is unexpectedly large")
            }
            val total = headerSize.takeIf { it > 0L } ?: expectedSize
            conn.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastProgressAt = 0L
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MAX_ARCHIVE_BYTES) {
                            throw IOException("The workflow artifact exceeded the download limit")
                        }
                        output.write(buffer, 0, count)
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 100L) {
                            lastProgressAt = now
                            onProgress(downloaded, total)
                        }
                    }
                    onProgress(downloaded, total)
                }
            }
            return
        }
    } catch (error: Exception) {
        destination.delete()
        throw error
    } finally {
        connection?.disconnect()
    }
}

/** Extracts exactly one safe APK entry and rejects traversal or archive bombs. */
internal fun extractSingleApk(
    archive: File,
    destination: File,
    maxApkBytes: Long = MAX_APK_BYTES,
): Long {
    destination.delete()
    var apkEntries = 0
    var extractedBytes = 0L
    try {
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val segments = name.split('/')
                val unsafe = name.startsWith('/') || name.startsWith('\\') ||
                    '\\' in name || segments.any { it == ".." }
                if (unsafe) throw IOException("The artifact contains an unsafe ZIP path")
                if (!entry.isDirectory && name.endsWith(".apk", ignoreCase = true)) {
                    apkEntries += 1
                    if (apkEntries > 1) {
                        throw IOException("The artifact contains more than one APK")
                    }
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            extractedBytes += count
                            if (extractedBytes > maxApkBytes) {
                                throw IOException("The APK exceeds the extraction limit")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        if (apkEntries != 1) throw IOException("The artifact does not contain exactly one APK")
        return extractedBytes
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}

private fun signingCertificateDigests(packageInfo: PackageInfo): Set<String> {
    @Suppress("DEPRECATION")
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = packageInfo.signingInfo ?: return emptySet()
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        packageInfo.signatures
    }.orEmpty()
    return signatures.mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
