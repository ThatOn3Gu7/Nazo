package quiz.thaton3app.nazo.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.FaceDetector
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Photograph-vs-anime gate for the Guessing Game's image pipeline.
 *
 * Problem: image searches for a character name often return REAL HUMANS
 * cosplaying the character — the title filter can't catch them because the
 * photos are titled with just the character's name. This gate looks at the
 * PIXELS instead and answers one question: "is this a photograph of the real
 * world rather than anime/manga artwork?"
 *
 * Two independent signals, both on-device and dependency-free:
 *
 *  1. PHOTO STATISTICS — anime is cel-shaded: large perfectly-flat color
 *     fills separated by hard ink outlines, and confident saturation.
 *     Photographs are the opposite: sensor noise and natural texture leave
 *     almost NO perfectly flat pixels, edges are soft luminance ramps, and
 *     real-world palettes are comparatively muted. Measured on a small
 *     analysis bitmap: flat-neighbour fraction, hard/soft edge ratio and
 *     mean saturation.
 *
 *  2. HUMAN FACE — `android.media.FaceDetector` is trained on photographic
 *     human faces; big-eyed anime faces usually defeat it. Here that bias is
 *     a FEATURE: a confident hit is strong evidence of a real person.
 *
 * Verdict ([looksLikeRealPhoto]): photographic statistics + a detected human
 * face, or overwhelmingly photographic statistics alone (crowd shots and
 * convention photos don't always have a detectable frontal face). On ANY
 * analysis failure the image is NOT rejected — the fetcher's other gates
 * still apply, and a wrongly-lost image would hurt more than a rare slip.
 *
 * Every verdict logs its raw metrics (tag [TAG]) so the thresholds can be
 * tuned from field logcat without guessing.
 */
object AnimeImageGate {
    private const val TAG = "NazoAnimeGate"

    /** Longest edge of the analysis bitmap. */
    private const val ANALYSIS_DIM = 224

    /** Longest edge of the RGB_565 face-detection copy. */
    private const val DETECT_DIM = 320

    /**
     * True when [bytes] look like a photograph of the real world (cosplay,
     * convention, statue, live-action still) rather than anime artwork.
     * Callers must be off the main thread (the fetcher runs on Dispatchers.IO).
     */
    fun looksLikeRealPhoto(bytes: ByteArray): Boolean {
        return try {
            val bmp = decodeCapped(bytes) ?: return false
            val stats = photoStats(bmp)
            val humanFace = hasHumanFace(bmp)
            bmp.recycle()

            var photoScore = 0
            // Almost nothing perfectly flat → continuous photographic texture.
            if (stats.flatFrac < 0.16f) photoScore += 2
            else if (stats.flatFrac < 0.28f) photoScore += 1
            // Soft luminance ramps dominate hard ink-like edges.
            if (stats.softEdgeFrac > 0f && stats.hardEdgeFrac / stats.softEdgeFrac < 0.22f) photoScore += 1
            // Muted real-world palette (mean saturation spread < ~16%).
            if (stats.satMean < 42f) photoScore += 1

            val verdict = photoScore >= 4 || (photoScore >= 2 && humanFace)
            Log.i(
                TAG,
                "flat=%.3f hard=%.3f soft=%.3f sat=%.1f face=%b score=%d -> %s".format(
                    stats.flatFrac, stats.hardEdgeFrac, stats.softEdgeFrac,
                    stats.satMean, humanFace, photoScore,
                    if (verdict) "REAL PHOTO (reject)" else "anime (accept)",
                ),
            )
            verdict
        } catch (e: Exception) {
            Log.w(TAG, "photo analysis failed — not rejecting", e)
            false
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "photo analysis OOM — not rejecting")
            false
        }
    }

    private class Stats(val flatFrac: Float, val hardEdgeFrac: Float, val softEdgeFrac: Float, val satMean: Float)

    private fun photoStats(bmp: Bitmap): Stats {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var flat = 0
        var hard = 0
        var soft = 0
        var satSum = 0L
        var compared = 0
        for (y in 0 until h - 1) {
            val row = y * w
            for (x in 0 until w - 1) {
                val i = row + x
                val c = px[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                satSum += (max(r, max(g, b)) - min(r, min(g, b))).toLong()
                val luma = (r * 77 + g * 150 + b * 29) shr 8
                // Right + down neighbours: color-flatness and luminance gradient.
                val cr = px[i + 1]
                val cd = px[i + w]
                val dR = abs(r - ((cr shr 16) and 0xFF)) + abs(g - ((cr shr 8) and 0xFF)) + abs(b - (cr and 0xFF))
                val dD = abs(r - ((cd shr 16) and 0xFF)) + abs(g - ((cd shr 8) and 0xFF)) + abs(b - (cd and 0xFF))
                if (dR <= 12 && dD <= 12) flat++
                val lr = (((cr shr 16) and 0xFF) * 77 + ((cr shr 8) and 0xFF) * 150 + (cr and 0xFF) * 29) shr 8
                val ld = (((cd shr 16) and 0xFF) * 77 + ((cd shr 8) and 0xFF) * 150 + (cd and 0xFF) * 29) shr 8
                val grad = max(abs(luma - lr), abs(luma - ld))
                if (grad > 48) hard++ else if (grad >= 10) soft++
                compared++
            }
        }
        val n = max(1, compared)
        return Stats(
            flatFrac = flat / n.toFloat(),
            hardEdgeFrac = hard / n.toFloat(),
            softEdgeFrac = soft / n.toFloat(),
            satMean = satSum / n.toFloat(),
        )
    }

    /** Confident frontal human face via the framework detector (RGB_565 copy). */
    private fun hasHumanFace(src: Bitmap): Boolean {
        return try {
            val scale = min(1f, DETECT_DIM / max(src.width, src.height).toFloat())
            var dw = max(2, (src.width * scale).toInt())
            if (dw % 2 == 1) dw -= 1
            val dh = max(2, (src.height * scale).toInt())
            val det = Bitmap.createBitmap(dw, dh, Bitmap.Config.RGB_565)
            Canvas(det).drawBitmap(src, null, Rect(0, 0, dw, dh), Paint(Paint.FILTER_BITMAP_FLAG))
            val faces = arrayOfNulls<FaceDetector.Face>(2)
            val found = FaceDetector(dw, dh, 2).findFaces(det, faces)
            det.recycle()
            for (i in 0 until found) {
                val f = faces[i] ?: continue
                if (f.confidence() >= 0.4f) return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "face check failed", e)
            false
        }
    }

    private fun decodeCapped(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val dim = max(bounds.outWidth, bounds.outHeight)
        while (dim / sample > ANALYSIS_DIM * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
