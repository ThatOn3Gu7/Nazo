package quiz.thaton3app.nazo.data.profile

/**
 * Pure policy for custom profile-cover videos.
 *
 * Cover videos are deliberately narrower than "anything MediaPlayer might
 * happen to open". A cover can decode at the same time as a video avatar, so
 * accepting a 4K/60 fps clip (or an unusual software-decoded codec) would make
 * the common dual-video case fail after selection. The import path therefore
 * accepts one predictable, hardware-friendly target:
 *
 * - MP4 container (sniffed by [AvatarMedia], never trusted from a file name);
 * - H.264 / AVC video track;
 * - at most 24 MB and 15 seconds;
 * - 1024x480 minimum effective frame, 1920x1080 / 2,073,600 pixels maximum;
 * - at most 30 fps (a small metadata tolerance avoids rejecting 30.000 fps).
 *
 * Android-side track/decoder/frame probing lives in
 * [ProfileImageStore.probeCoverVideo]. Keeping the limits and messages here
 * makes the policy directly testable on the JVM.
 */
internal object CoverVideoMedia {

    const val MAX_BYTES = 24L * 1024 * 1024
    const val MAX_DURATION_MS = 15_000L
    const val MIN_LONG_EDGE = 1024
    const val MIN_SHORT_EDGE = 480
    const val MAX_LONG_EDGE = 1920
    const val MAX_SHORT_EDGE = 1080
    const val MAX_PIXELS = 1920L * 1080L
    const val MAX_FRAME_RATE = 30f
    const val SUPPORTED_CODEC_MIME = "video/avc"

    const val UNPLAYABLE_ERROR =
        "That video couldn't be played on this device. Try re-exporting it as an H.264 MP4."

    const val UNSUPPORTED_FORMAT_ERROR =
        "That video format isn't supported for covers. Pick an H.264 MP4 video."

    fun isVideoUri(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return uri.substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .equals("mp4", ignoreCase = true)
    }

    fun sizeError(sizeBytes: Long): String? =
        if (sizeBytes > MAX_BYTES) {
            "That video is too large for a cover (24 MB maximum). " +
                "Try a shorter clip or a lower bitrate."
        } else {
            null
        }

    fun durationError(durationMs: Long): String? =
        if (durationMs > MAX_DURATION_MS) {
            "That video is too long — cover videos must be 15 seconds or less. " +
                "Trim the clip and try again."
        } else {
            null
        }

    /**
     * Dimensions are orientation-independent here: [long] / [short] accept
     * landscape, portrait and square sources equally, while the WYSIWYG
     * preview shows what centre-crop will retain in the wide banner.
     */
    fun dimensionError(width: Int, height: Int): String? {
        if (width <= 0 || height <= 0) {
            return "That video has no readable picture size."
        }
        val long = maxOf(width, height)
        val short = minOf(width, height)
        return when {
            long < MIN_LONG_EDGE || short < MIN_SHORT_EDGE ->
                "That video is too small for a cover — use at least 1024 × 480 pixels."

            long > MAX_LONG_EDGE || short > MAX_SHORT_EDGE ||
                width.toLong() * height.toLong() > MAX_PIXELS ->
                "That video's resolution is too high for a cover. Use 1080p " +
                    "(1920 × 1080) or smaller."

            else -> null
        }
    }

    fun frameRateError(frameRate: Float?): String? =
        if (frameRate != null && frameRate > MAX_FRAME_RATE + 0.5f) {
            "That video's frame rate is too high for a cover (30 fps maximum). " +
                "Re-export it at 30 fps and try again."
        } else {
            null
        }

    fun codecError(mime: String?): String? =
        if (!mime.equals(SUPPORTED_CODEC_MIME, ignoreCase = true)) {
            "That video's codec isn't supported for covers. Re-export it as " +
                "H.264 / AVC in an MP4 container."
        } else {
            null
        }
}

/** Metadata returned only after the entire cover-video policy has passed. */
internal data class CoverVideoInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val frameRate: Float?,
    val codecMime: String,
    val sizeBytes: Long,
)
