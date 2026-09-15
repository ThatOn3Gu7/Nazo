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
                val normalised = normaliseUrl(url)
                val target = File(draftDir(context), "draft_${System.nanoTime()}")
                try {
                    fetchInto(normalised, target)
                    // Content-Type lies often enough that the real test is
                    // whether Android can decode what we actually received.
                    // This also rejects an HTML error page served with a 200.
                    if (!isDecodableImage(target)) {
                        error(
                            "That link didn't return a readable image. SVG files " +
                                "aren't supported -- try a JPG, PNG or WebP link.",
                        )
                    }
                    target
                } catch (t: Throwable) {
                    target.delete()
                    throw t
                }
            }.recoverCatching { throwable ->
                throw IllegalStateException(friendlyMessage(throwable), throwable)
            }
        }

    /**
     * Accepts what a user would reasonably paste.
     *
     * People paste links with stray whitespace, without a scheme
     * ("w7.pngwing.com/x.png"), or copied with the scheme uppercased. Rejecting
     * those as "unsupported" is user-hostile when the fix is obvious.
     */
    private fun normaliseUrl(raw: String): URL {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Enter a link first." }
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            // Protocol-relative URLs, as copied from some page sources.
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.contains("://") ->
                throw IllegalArgumentException("Only http and https links are supported.")
            else -> "https://$trimmed"
        }
        return runCatching { URL(withScheme) }.getOrElse {
            throw IllegalArgumentException("That doesn't look like a valid link.")
        }
    }

    /**
     * Downloads [url] into [target], following redirects manually.
     *
     * HttpURLConnection follows redirects automatically but REFUSES to follow
     * one that switches protocol (http -> https and vice versa), silently
     * handing back the 30x instead. That is extremely common on image hosts, so
     * the redirect chain is walked here.
     *
     * The request also mimics a browser. Many image CDNs (pngwing among them)
     * serve 403 to unknown user agents or to requests with no Referer, as
     * hotlink protection -- a plain library user agent gets refused for images
     * that open fine in a browser.
     */
    private fun fetchInto(url: URL, target: File) {
        var current = url
        var redirects = 0

        while (true) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                // Handled manually below so cross-protocol hops work.
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                )
                setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                )
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                // Same-origin referer satisfies the usual hotlink checks.
                setRequestProperty("Referer", "${current.protocol}://${current.host}/")
            }

            try {
                val code = connection.responseCode

                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Server returned HTTP $code.")
                    redirects++
                    require(redirects <= 5) { "That link redirects too many times." }
                    // Relative Locations are legal, so resolve against current.
                    current = URL(current, location)
                    continue
                }

                require(code in 200..299) {
                    when (code) {
                        403 -> "That site blocked the download (403). Try a direct image link."
                        404 -> "Nothing found at that link (404)."
                        else -> "Server returned HTTP $code."
                    }
                }

                // contentLengthLong is -1 when the server uses chunked
                // encoding, so only treat a POSITIVE value as a real limit.
                val declared = connection.contentLengthLong
                require(declared <= MAX_DOWNLOAD_BYTES) {
                    "That image is too large (over 16 MB)."
                }

                var total = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            total += read
                            if (total > MAX_DOWNLOAD_BYTES) {
                                error("That image is too large (over 16 MB).")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                require(total > 0L) { "That link returned an empty file." }
                return
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun friendlyMessage(throwable: Throwable): String {
        val message = throwable.message
        return when {
            // require()/error() already produce user-facing wording.
            !message.isNullOrBlank() && throwable is IllegalArgumentException -> message
            !message.isNullOrBlank() && throwable is IllegalStateException -> message
            throwable is java.net.UnknownHostException ->
                "Couldn't reach that site. Check the link and your connection."
            throwable is java.net.SocketTimeoutException ->
                "That site took too long to respond."
            throwable is javax.net.ssl.SSLException ->
                "Couldn't establish a secure connection to that site."
            throwable is java.io.IOException ->
                "Couldn't download that image. Check your connection and try again."
            else -> "Couldn't load that image. Check the link and try again."
        }
    }

    /**
     * True if Android can actually decode [file] as an image.
     *
     * Checks the decoded BOUNDS rather than the return value: with
     * inJustDecodeBounds set, decodeStream always returns null and only fills
     * outWidth/outHeight.
     */
    private fun isDecodableImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        }
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
            // NOTE: decodeStream returns null BY CONTRACT when
            // inJustDecodeBounds is set -- it only populates outWidth/outHeight.
            // The elvis must therefore test the STREAM, never the decode
            // result, or every valid image is treated as unreadable.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = openStream(context, source) ?: return@runCatching null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
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
            val pixelStream = openStream(context, source) ?: return@runCatching null
            val bitmap = pixelStream.use {
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
                // Transparent source (very common for PNG logos and cut-outs):
                // JPEG has no alpha channel, so transparent pixels would encode
                // as BLACK. Save those as PNG instead of silently ruining them.
                val transparent = scaled.hasAlpha()
                val extension = if (transparent) "png" else "jpg"
                val target = File(
                    avatarDir(context),
                    "avatar_${System.currentTimeMillis()}.$extension",
                )
                target.outputStream().use { out ->
                    if (transparent) {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } else {
                        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
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
