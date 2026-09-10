package quiz.thaton3app.nazo.vision

import android.graphics.Bitmap
import android.util.Log
import quiz.thaton3app.nazo.BuildConfig

/**
 * Temporary instrumentation for the mystery-image corruption.
 *
 * The reported symptom is a blown-out white image where only the darkest ink
 * (eye outlines, hair spikes) survives. That is the signature of pixel values
 * being interpreted in the wrong numeric range — e.g. a half-float
 * (`RGBA_F16`) or wide-gamut decode being read as 8-bit sRGB, where everything
 * above 1.0 clamps to 0xFF.
 *
 * Rather than guess again, this logs what each pipeline stage actually
 * produces so the cause can be read off logcat:
 *
 *     adb logcat -s NazoImgDiag
 *
 * Debug builds only — [log] compiles to a no-op check in release.
 */
object ImageDiagnostics {
    const val TAG = "NazoImgDiag"

    /**
     * Logs the format of [bytes] and/or the configuration and a few sampled
     * pixels of [bitmap], labelled with [step].
     *
     * Sampling uses the four corners plus the centre. `getPixel` always returns
     * sRGB-converted ARGB, so a bitmap that LOOKS correct here while rendering
     * white on screen points at the draw path rather than the decode.
     */
    fun log(step: String, bytes: ByteArray? = null, bitmap: Bitmap? = null) {
        if (!BuildConfig.DEBUG) return
        try {
            if (bytes != null) {
                val magic = bytes.take(12).joinToString(" ") { "%02X".format(it) }
                Log.d(TAG, "[$step] bytes=${bytes.size} magic=$magic type=${sniff(bytes)}")
            }
            if (bitmap != null) {
                Log.d(
                    TAG,
                    "[$step] ${bitmap.width}x${bitmap.height} config=${bitmap.config} " +
                        "colorSpace=${bitmap.colorSpace?.name} hasAlpha=${bitmap.hasAlpha()} " +
                        "premultiplied=${bitmap.isPremultiplied}",
                )
                val w = bitmap.width - 1
                val h = bitmap.height - 1
                if (w >= 0 && h >= 0) {
                    val pts = listOf(
                        "tl" to (0 to 0),
                        "tr" to (w to 0),
                        "bl" to (0 to h),
                        "br" to (w to h),
                        "mid" to (w / 2 to h / 2),
                    )
                    val samples = pts.joinToString(" ") { (name, p) ->
                        "$name=%08X".format(bitmap.getPixel(p.first, p.second))
                    }
                    Log.d(TAG, "[$step] px $samples")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$step] diagnostics failed", e)
        }
    }

    /** Identifies the container from its header, independent of the URL's extension. */
    private fun sniff(b: ByteArray): String = when {
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> "JPEG"
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() -> "PNG"
        b.size >= 12 && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "WEBP"
        b.size >= 6 && String(b, 0, 3, Charsets.US_ASCII) == "GIF" -> "GIF"
        b.size >= 12 && String(b, 4, 8, Charsets.US_ASCII).startsWith("ftyp") -> "HEIF/AVIF"
        else -> "unknown"
    }
}
