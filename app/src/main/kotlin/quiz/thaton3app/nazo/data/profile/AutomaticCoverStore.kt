package quiz.thaton3app.nazo.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Bounded, app-private cache for avatar-derived covers.
 *
 * Static avatars are processed once through a tiny CPU work image: dominant
 * colours are measured from the whole avatar, a 160x90 centre crop receives a
 * three-pass box blur, then that result is upscaled under soft colour fields to
 * 1024x576. There is no per-frame blur and no full-resolution blur allocation.
 *
 * MP4 avatars are exported once by Media3 Transformer through its OpenGL effect
 * pipeline: crop-to-fill, at most 960x540/24 fps, Gaussian blur, H.264, and no audio.
 * The resulting file is played by the existing independent cover MediaPlayer;
 * Media3 is not part of playback and owns no long-lived player or decoder.
 */
@UnstableApi
internal object AutomaticCoverStore {
    private const val DIR = "profile_auto_covers"
    private const val STATIC_WORK_WIDTH = 160
    private const val STATIC_WORK_HEIGHT = 90
    private const val STATIC_BLUR_RADIUS = 11

    fun cachedArtifact(context: Context, sourceUri: String?): AutomaticCoverArtifact? {
        if (!sourceAvailable(sourceUri)) return null
        val source = sourceUri ?: return null
        val kind = AutomaticCoverMedia.sourceKind(source)
        val file = when (kind) {
            AutomaticCoverMedia.SourceKind.STATIC_IMAGE ->
                File(directory(context), "auto_${fingerprint(source)}.jpg")
            AutomaticCoverMedia.SourceKind.VIDEO ->
                File(directory(context), "auto_${fingerprint(source)}.mp4")
            else -> return null
        }
        // Files are written through a temp path and renamed only after decode
        // validation, so existence + non-zero length is a safe, cheap startup
        // cache hit (no bitmap/video decode on the composition thread).
        if (!file.isFile || file.length() <= 0L) return null
        return AutomaticCoverArtifact(
            sourceUri = source,
            uri = Uri.fromFile(file).toString(),
            kind = if (kind == AutomaticCoverMedia.SourceKind.VIDEO) {
                AutomaticCoverKind.VIDEO
            } else {
                AutomaticCoverKind.IMAGE
            },
        )
    }

    suspend fun prepare(context: Context, sourceUri: String?): Result<AutomaticCoverArtifact> {
        val kind = AutomaticCoverMedia.sourceKind(sourceUri)
        val source = sourceUri
        if (source == null || kind == AutomaticCoverMedia.SourceKind.NONE) {
            return Result.failure(
                IllegalStateException("Add a photo or MP4 video avatar to use an automatic cover.")
            )
        }
        if (kind == AutomaticCoverMedia.SourceKind.UNSUPPORTED_ANIMATION) {
            return Result.failure(
                IllegalStateException(
                    "Automatic covers currently support static photos and MP4 video avatars. " +
                        "Choose one of those instead of a GIF or animated WebP."
                )
            )
        }
        if (!sourceAvailable(source)) {
            return Result.failure(
                IllegalStateException("Your avatar file is no longer available. Choose it again first.")
            )
        }
        cachedArtifact(context, source)?.let { return Result.success(it) }

        return try {
            Result.success(
                when (kind) {
                    AutomaticCoverMedia.SourceKind.STATIC_IMAGE -> prepareStatic(context, source)
                    AutomaticCoverMedia.SourceKind.VIDEO -> prepareVideo(context, source)
                    else -> error("That avatar can't be used for an automatic cover.")
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            Result.failure(
                IllegalStateException(
                    throwable.message?.takeIf { it.isNotBlank() }
                        ?: "Couldn't prepare an automatic cover from that avatar.",
                    throwable,
                )
            )
        }
    }

    fun discardAll(context: Context) {
        runCatching { directory(context).listFiles()?.forEach { file -> file.delete() } }
    }

    private suspend fun prepareStatic(
        context: Context,
        sourceUri: String,
    ): AutomaticCoverArtifact = withContext(Dispatchers.IO) {
        val draft = if (sourceUri.startsWith("http://") || sourceUri.startsWith("https://")) {
            ProfileImageStore.downloadDraft(context, sourceUri).getOrThrow()
        } else {
            null
        }
        try {
            val source = draft?.let(ProfileImageSource::LocalFile) ?: sourceFor(sourceUri)
                ?: error("Your avatar file couldn't be opened.")
            val bitmap = ProfileImageStore.decodeForEditing(context, source)
                ?: error("Your avatar image couldn't be decoded.")
            try {
                val dir = directory(context)
                val target = File(dir, "auto_${fingerprint(sourceUri)}.jpg")
                val temp = File(dir, target.name + ".tmp")
                temp.delete()
                val generated = renderStatic(bitmap)
                try {
                    temp.outputStream().use { output ->
                        check(generated.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                            "Couldn't save the generated cover."
                        }
                    }
                    check(temp.length() > 0L) { "The generated cover was empty." }
                    replaceAtomically(temp, target)
                    pruneExcept(dir, target)
                    AutomaticCoverArtifact(
                        sourceUri = sourceUri,
                        uri = Uri.fromFile(target).toString(),
                        kind = AutomaticCoverKind.IMAGE,
                    )
                } finally {
                    generated.recycle()
                    temp.delete()
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            draft?.let(ProfileImageStore::discardDraft)
        }
    }

    private fun renderStatic(source: Bitmap): Bitmap {
        val analysis = Bitmap.createScaledBitmap(source, 96, 96, true)
        val samplePixels = IntArray(96 * 96)
        analysis.getPixels(samplePixels, 0, 96, 0, 0, 96, 96)
        analysis.recycle()
        val palette = AutomaticCoverMedia.dominantPalette(samplePixels)

        val work = Bitmap.createBitmap(STATIC_WORK_WIDTH, STATIC_WORK_HEIGHT, Bitmap.Config.ARGB_8888)
        val workCanvas = Canvas(work)
        workCanvas.drawColor(palette[0])
        workCanvas.drawBitmap(
            source,
            cropRect(source.width, source.height, STATIC_WORK_WIDTH, STATIC_WORK_HEIGHT),
            Rect(0, 0, STATIC_WORK_WIDTH, STATIC_WORK_HEIGHT),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        val workPixels = IntArray(STATIC_WORK_WIDTH * STATIC_WORK_HEIGHT)
        work.getPixels(workPixels, 0, STATIC_WORK_WIDTH, 0, 0, STATIC_WORK_WIDTH, STATIC_WORK_HEIGHT)
        work.recycle()
        val blurredPixels = AutomaticCoverMedia.blurOpaque(
            pixels = workPixels,
            width = STATIC_WORK_WIDTH,
            height = STATIC_WORK_HEIGHT,
            radius = STATIC_BLUR_RADIUS,
        )
        val blurred = Bitmap.createBitmap(
            blurredPixels,
            STATIC_WORK_WIDTH,
            STATIC_WORK_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )

        val output = Bitmap.createBitmap(
            AutomaticCoverMedia.STATIC_WIDTH,
            AutomaticCoverMedia.STATIC_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        canvas.drawBitmap(
            blurred,
            null,
            RectF(0f, 0f, output.width.toFloat(), output.height.toFloat()),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        blurred.recycle()

        val colourField = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                output.width.toFloat(),
                output.height.toFloat(),
                intArrayOf(withAlpha(palette[0], 86), withAlpha(palette[1], 72), withAlpha(palette[2], 82)),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), colourField)
        val glow = Paint().apply {
            shader = RadialGradient(
                output.width * 0.72f,
                output.height * 0.24f,
                output.width * 0.62f,
                withAlpha(palette[2], 54),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), glow)
        canvas.drawColor(0x16000000)
        return output
    }

    private suspend fun prepareVideo(
        context: Context,
        sourceUri: String,
    ): AutomaticCoverArtifact {
        val metadata = videoMetadata(context, sourceUri)
            ?: error("Your avatar video couldn't be read.")
        val (width, height) = AutomaticCoverMedia.videoOutputSize(
            metadata.width,
            metadata.height,
            metadata.rotation,
        ) ?: error("Your avatar video has no readable frame size.")

        val dir = directory(context)
        val target = File(dir, "auto_${fingerprint(sourceUri)}.mp4")
        val temp = File(dir, target.name + ".tmp.mp4")
        withContext(Dispatchers.IO) { temp.delete() }
        try {
            exportVideo(
                context = context.applicationContext,
                sourceUri = sourceUri,
                output = temp,
                width = width,
                height = height,
            )
            check(withContext(Dispatchers.IO) { videoIsReadable(temp) }) {
                "The generated video couldn't be played."
            }
            withContext(Dispatchers.IO) {
                replaceAtomically(temp, target)
                pruneExcept(dir, target)
            }
            return AutomaticCoverArtifact(
                sourceUri = sourceUri,
                uri = Uri.fromFile(target).toString(),
                kind = AutomaticCoverKind.VIDEO,
            )
        } finally {
            // File.delete is bounded metadata I/O; keep cleanup cancellation-
            // proof rather than entering a cancelled withContext block.
            runCatching { temp.delete() }
        }
    }

    private suspend fun exportVideo(
        context: Context,
        sourceUri: String,
        output: File,
        width: Int,
        height: Int,
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            lateinit var transformer: Transformer
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(Unit))
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(exportException))
                    }
                }
            }

            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                output.delete()
            }
            val effects = Effects(
                emptyList(),
                listOf(
                    FrameDropEffect.createDefaultFrameDropEffect(
                        AutomaticCoverMedia.VIDEO_FRAME_RATE
                    ),
                    Presentation.createForWidthAndHeight(
                        width,
                        height,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                    ),
                    GaussianBlur(AutomaticCoverMedia.VIDEO_BLUR_SIGMA),
                ),
            )
            val item = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
                .setRemoveAudio(true)
                .setEffects(effects)
                .build()
            runCatching { transformer.start(item, output.absolutePath) }
                .onFailure { failure ->
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(failure))
                    }
                }
        }
    }

    private data class VideoMetadata(val width: Int, val height: Int, val rotation: Int)

    private fun videoMetadata(context: Context, uri: String): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            VideoMetadata(width, height, rotation)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun videoIsReadable(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val okay = duration > 0L && frame != null && frame.width > 0 && frame.height > 0
            frame?.recycle()
            okay
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sourceFor(uri: String): ProfileImageSource? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        return when (parsed.scheme?.lowercase()) {
            "file" -> parsed.path?.let(::File)?.let(ProfileImageSource::LocalFile)
            "content" -> ProfileImageSource.ContentUri(parsed)
            else -> null
        }
    }

    private fun sourceAvailable(uri: String?): Boolean {
        if (uri.isNullOrBlank() || uri.startsWith("emoji:")) return false
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        return when (parsed.scheme?.lowercase()) {
            "file" -> parsed.path?.let(::File)?.isFile == true
            "content", "http", "https" -> true
            else -> false
        }
    }

    private fun cropRect(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Rect {
        val sourceRatio = sourceWidth.toFloat() / sourceHeight
        val targetRatio = targetWidth.toFloat() / targetHeight
        return if (sourceRatio > targetRatio) {
            val width = (sourceHeight * targetRatio).toInt().coerceAtLeast(1)
            val left = (sourceWidth - width) / 2
            Rect(left, 0, left + width, sourceHeight)
        } else {
            val height = (sourceWidth / targetRatio).toInt().coerceAtLeast(1)
            val top = (sourceHeight - height) / 2
            Rect(0, top, sourceWidth, top + height)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(10).joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun pruneExcept(directory: File, keep: File) {
        directory.listFiles()?.forEach { file -> if (file != keep) file.delete() }
    }

    private fun replaceAtomically(temp: File, target: File) {
        if (target.exists() && !target.delete()) error("Couldn't replace the old generated cover.")
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
