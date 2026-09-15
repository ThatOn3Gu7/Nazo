package quiz.thaton3app.nazo.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import java.net.HttpURLConnection
import java.net.URL

/**
 * Storage and lifecycle for profile pictures.
 *
 * Three rules drive the design:
 *
 *  1. **Never modify the user's original file.** A gallery pick gives us a
 *     `content://` URI we can only read. Cropping therefore always decodes into
 *     memory and writes a NEW file into our own storage — the original is never
 *     opened for writing, so editing is non-destructive by construction rather
 *     than by discipline.
 *  2. **Accepted avatars are permanent, drafts are not.** Accepted images live
 *     in `filesDir/profile/`; work-in-progress downloads live in
 *     `cacheDir/profile_drafts/`. Keeping them in separate directories means
 *     "clean up the draft" can never delete a live avatar.
 *  3. **A gallery `content://` URI is not durable.** The permission can be
 *     revoked and the underlying photo can be deleted, which would leave the
 *     user with a broken avatar later. Once an image is accepted we own a copy,
 *     so the avatar keeps working regardless.
 */
object ProfileImageStore {

    private const val AVATAR_DIR = "profile"
    private const val DRAFT_DIR = "profile_drafts"

    /** Long edge of a saved avatar. Plenty for a 96 dp circle on any density. */
    private const val OUTPUT_SIZE = 1024

    /** Refuse absurd downloads rather than OOM-ing on a hostile URL. */
    private const val MAX_DOWNLOAD_BYTES = 16L * 1024 * 1024

    private fun avatarDir(context: Context): File =
        File(context.filesDir, AVATAR_DIR).apply { mkdirs() }

    private fun draftDir(context: Context): File =
        File(context.cacheDir, DRAFT_DIR).apply { mkdirs() }

    // -----------------------------------------------------------------------
    // Downloading
    // -----------------------------------------------------------------------

    /**
     * Downloads [url] into a draft file so it can be previewed and cropped
     * offline of the network.
     *
     * Returns the local file, or a [Result] failure carrying a message that is
     * safe to show the user. The caller owns the returned file and must pass it
     * to [discardDraft] if the user abandons it.
     */
    suspend fun downloadDraft(context: Context, url: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = URL(url)
                require(parsed.protocol == "http" || parsed.protocol == "https") {
                    "Only http and https links are supported."
                }

                val connection = (parsed.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    // Some CDNs serve a 403 to the default Java user agent.
                    setRequestProperty("User-Agent", "Nazo-Android")
                    setRequestProperty("Accept", "image/*")
                }

                try {
                    val code = connection.responseCode
                    require(code in 200..299) { "Server returned HTTP $code." }

                    val declaredLength = connection.contentLengthLong
                    require(declaredLength <= MAX_DOWNLOAD_BYTES) {
                        "That image is too large (over 16 MB)."
                    }

                    val target = File(draftDir(context), "draft_${System.nanoTime()}")
                    var total = 0L
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                total += read
                                if (total > MAX_DOWNLOAD_BYTES) {
                                    target.delete()
                                    error("That image is too large (over 16 MB).")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    // Content-Type lies often enough that we verify by actually
                    // decoding the bounds. This also rejects an HTML error page
                    // that was served with a 200.
                    if (!isDecodableImage(target)) {
                        target.delete()
                        error("That link doesn't point to an image we can read.")
                    }
                    target
                } finally {
                    connection.disconnect()
                }
            }.recoverCatching { throwable ->
                throw IllegalStateException(friendlyMessage(throwable), throwable)
            }
        }

    private fun friendlyMessage(throwable: Throwable): String {
        val message = throwable.message
        return when {
            !message.isNullOrBlank() && throwable is IllegalArgumentException -> message
            !message.isNullOrBlank() && throwable is IllegalStateException -> message
            throwable is java.net.UnknownHostException ->
                "Couldn't reach that site. Check the link and your connection."
            throwable is java.net.SocketTimeoutException ->
                "That site took too long to respond."
            else -> "Couldn't load that image. Check the link and try again."
        }
    }

    private fun isDecodableImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        return options.outWidth > 0 && options.outHeight > 0
    }

    // -----------------------------------------------------------------------
    // Decoding
    // -----------------------------------------------------------------------

    /**
     * Decodes [source] downsampled to roughly [maxEdge], with EXIF rotation
     * applied.
     *
     * Downsampling matters: phone cameras produce 50 MP images that would
     * allocate ~200 MB as a full bitmap and reliably OOM inside a crop UI that
     * also holds the output. Honouring EXIF matters because otherwise portrait
     * photos load sideways and the user crops a rotated image.
     */
    suspend fun decodeForEditing(
        context: Context,
        source: ProfileImageSource,
        maxEdge: Int = 2048,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return@runCatching null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (
                bounds.outWidth / (sample * 2) >= maxEdge ||
                bounds.outHeight / (sample * 2) >= maxEdge
            ) {
                sample *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = openStream(context, source)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@runCatching null

            applyExifRotation(context, source, bitmap)
        }.getOrNull()
    }

    private fun openStream(context: Context, source: ProfileImageSource) = when (source) {
        is ProfileImageSource.LocalFile -> source.file.inputStream()
        is ProfileImageSource.ContentUri ->
            context.contentResolver.openInputStream(source.uri)
    }

    private fun applyExifRotation(
        context: Context,
        source: ProfileImageSource,
        bitmap: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            openStream(context, source)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { rotated -> if (rotated != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    // -----------------------------------------------------------------------
    // Saving
    // -----------------------------------------------------------------------

    /**
     * Writes [bitmap] as the user's avatar and returns a `file://` URI for it.
     *
     * Old avatars are swept afterwards so the directory cannot grow without
     * bound — the filename is timestamped rather than fixed because reusing one
     * filename means Coil serves the stale cached image after an edit.
     */
    suspend fun saveAvatar(context: Context, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val scaled = scaleToFit(bitmap, OUTPUT_SIZE)
                val target = File(avatarDir(context), "avatar_${System.currentTimeMillis()}.jpg")
                target.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                if (scaled != bitmap) scaled.recycle()
                pruneOldAvatars(context, keep = target)
                Uri.fromFile(target).toString()
            }
        }

    /**
     * Centre-crops to a square.
     *
     * The preview renders with ContentScale.Crop, i.e. a centred square. If
     * "Accept" saved the raw rectangle instead, a wide photo would be stored
     * un-cropped and the avatar circle would then frame it differently from
     * what the user just approved. Cropping here keeps preview and result
     * identical. Allocates a new bitmap; the source is not mutated.
     */
    fun centerCropSquare(bitmap: Bitmap): Bitmap {
        if (bitmap.width == bitmap.height) return bitmap
        val edge = min(bitmap.width, bitmap.height)
        val left = (bitmap.width - edge) / 2
        val top = (bitmap.height - edge) / 2
        return runCatching {
            Bitmap.createBitmap(bitmap, left, top, edge, edge)
        }.getOrDefault(bitmap)
    }

    private fun scaleToFit(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun pruneOldAvatars(context: Context, keep: File) {
        runCatching {
            avatarDir(context).listFiles()?.forEach { file ->
                if (file != keep) file.delete()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Draft cleanup
    // -----------------------------------------------------------------------

    /** Deletes a single abandoned draft. Safe to call with null or twice. */
    fun discardDraft(file: File?) {
        if (file == null) return
        runCatching { if (file.exists()) file.delete() }
    }

    /**
     * Deletes every draft left behind.
     *
     * Drafts are removed as soon as the user backs out, but a process death
     * mid-edit would strand one, so this runs at startup as a safety net. It
     * only ever touches `cacheDir/profile_drafts`, never a saved avatar.
     */
    fun clearAllDrafts(context: Context) {
        runCatching {
            draftDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}

/** Where an image being edited is being read from. */
sealed interface ProfileImageSource {
    /** A downloaded draft, or any file we own. */
    data class LocalFile(val file: File) : ProfileImageSource

    /** A gallery pick. Read-only: we never write back to this. */
    data class ContentUri(val uri: Uri) : ProfileImageSource
}
