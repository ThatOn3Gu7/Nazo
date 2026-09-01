package quiz.thaton3app.nazo.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Passport-style portrait reframing for the Guessing Game's mystery images.
 *
 * Fetched images are often full-body art or wide scenes where the character's
 * face — the thing the player is meant to recognise — is a small fraction of
 * the frame. [toPassportPortrait] finds the face and re-crops the image to a
 * 3:4 head-and-shoulders portrait, like a passport photo, regardless of the
 * source resolution or aspect.
 *
 * Detection is a two-stage hybrid, entirely on-device with ZERO new
 * dependencies:
 *
 *   1. `android.media.FaceDetector` — the framework's eye-pair detector
 *      (ships in the OS since API 1). Precise when it fires, but it is
 *      trained on human faces, so stylised anime faces often defeat it.
 *   2. A custom anime-face heuristic tuned for exactly those cases: on a
 *      small analysis bitmap it builds a skin-tone mask (broad warm band that
 *      covers pale-to-dark anime palettes while rejecting saturated
 *      hair/clothes hues), extracts connected skin regions, and keeps only
 *      blobs that LOOK like faces — compact (high fill density, near-square
 *      aspect, so arms/legs/torsos fail), containing dark "eye evidence" in
 *      their upper portion (large dark eyes are the anime signature; hands
 *      and necks have none), weighted toward the top of the image where
 *      faces live in character art.
 *
 * CONFIDENCE GATE (owner decision): when neither stage finds a plausible
 * face, the ORIGINAL bytes are returned untouched — a full-body shot beats a
 * confidently wrong crop of a kneecap. Every error path also returns the
 * original bytes, so this step can never lose an image the fetcher won.
 *
 * Runs in the play screen's pre-warm coroutine BEFORE the countdown starts
 * (typically 30–80 ms on a mid-range phone), so the round timer is never
 * affected. Decoding is capped at [MAX_SRC_DIM] via power-of-two sampling —
 * the full-resolution image is never held in memory (same policy as
 * PixelReveal).
 */
object PortraitCrop {
    private const val TAG = "NazoPortraitCrop"

    /** Longest edge the source is decoded at (low-RAM cap, same as PixelReveal). */
    private const val MAX_SRC_DIM = 1600

    /** Longest edge of the framework-detector bitmap (RGB_565, speed cap). */
    private const val DETECT_DIM = 480

    /** Longest edge of the heuristic analysis bitmap. */
    private const val ANALYSIS_DIM = 160

    /** Output portrait max height (3:4 → at most 900 x 1200). */
    private const val OUT_H_MAX = 1200

    /** Passport aspect: width / height. */
    private const val ASPECT = 3f / 4f

    /**
     * Reframes [bytes] to a 3:4 portrait around the detected face, re-encoded
     * as JPEG (or PNG when the source has transparency). Returns the ORIGINAL
     * array when no face is found with confidence or anything at all fails.
     */
    suspend fun toPassportPortrait(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        try {
            val src = decodeCapped(bytes) ?: return@withContext bytes
            val face = detectWithFramework(src) ?: detectAnimeHeuristic(src)
            if (face == null) {
                Log.i(TAG, "no confident face — keeping original image")
                src.recycle()
                return@withContext bytes
            }
            val frame = passportFrame(face, src.width, src.height)
            if (frame == null) {
                // Face too small to trust, or the frame is basically the whole
                // image already (e.g. AniList head-shots) — nothing to gain.
                src.recycle()
                return@withContext bytes
            }
            var out = Bitmap.createBitmap(src, frame.left, frame.top, frame.width(), frame.height())
            if (out !== src) src.recycle()
            if (out.height > OUT_H_MAX) {
                val s = OUT_H_MAX / out.height.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    out, max(1, (out.width * s).toInt()), OUT_H_MAX, true,
                )
                if (scaled !== out) out.recycle()
                out = scaled
            }
            val bos = ByteArrayOutputStream(128 * 1024)
            val ok = if (out.hasAlpha()) {
                out.compress(Bitmap.CompressFormat.PNG, 100, bos)
            } else {
                out.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            }
            out.recycle()
            if (ok) {
                Log.i(TAG, "cropped to passport portrait ${frame.width()}x${frame.height()}")
                bos.toByteArray()
            } else {
                bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "portrait crop failed — keeping original image", e)
            bytes
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "portrait crop OOM — keeping original image")
            bytes
        }
    }

    // ---- decoding -------------------------------------------------------

    private fun decodeCapped(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val dim = max(bounds.outWidth, bounds.outHeight)
        while (dim / sample > MAX_SRC_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    // ---- stage 1: framework eye-pair detector ----------------------------

    /**
     * Runs the OS face detector on a small RGB_565 copy (its required
     * format; width must be even). Returns a face rect in [src] coordinates
     * built from the eye midpoint and eye distance, or null when nothing
     * clears the confidence bar.
     */
    private fun detectWithFramework(src: Bitmap): RectF? {
        return try {
            val scale = min(1f, DETECT_DIM / max(src.width, src.height).toFloat())
            var dw = max(2, (src.width * scale).toInt())
            if (dw % 2 == 1) dw -= 1
            val dh = max(2, (src.height * scale).toInt())
            val det = Bitmap.createBitmap(dw, dh, Bitmap.Config.RGB_565)
            Canvas(det).drawBitmap(src, null, Rect(0, 0, dw, dh), Paint(Paint.FILTER_BITMAP_FLAG))
            val faces = arrayOfNulls<FaceDetector.Face>(4)
            val found = FaceDetector(dw, dh, 4).findFaces(det, faces)
            det.recycle()
            var best: FaceDetector.Face? = null
            for (i in 0 until found) {
                val f = faces[i] ?: continue
                if (f.confidence() < 0.35f) continue
                if (best == null || f.confidence() > best.confidence()) best = f
            }
            val face = best ?: return null
            val mid = PointF()
            face.getMidPoint(mid)
            val eyeDist = face.eyesDistance()
            if (eyeDist <= 0f) return null
            val sx = src.width / dw.toFloat()
            val sy = src.height / dh.toFloat()
            val mx = mid.x * sx
            val my = mid.y * sy
            val d = eyeDist * sx
            // Face box from the eye pair: ~2.4 eye-distances wide, forehead
            // ~1.5 above the eye line, chin ~2.1 below.
            RectF(mx - 1.2f * d, my - 1.5f * d, mx + 1.2f * d, my + 2.1f * d)
        } catch (e: Exception) {
            Log.w(TAG, "framework face detection failed", e)
            null
        }
    }

    // ---- stage 2: custom anime-face heuristic -----------------------------

    /**
     * Finds the most face-like connected skin region (see class KDoc for the
     * full rationale). Returns a face rect in [src] coordinates — already
     * extended upward to include the hairline — or null when no blob passes
     * all the plausibility gates.
     */
    private fun detectAnimeHeuristic(src: Bitmap): RectF? {
        val scale = min(1f, ANALYSIS_DIM / max(src.width, src.height).toFloat())
        val aw = max(16, (src.width * scale).toInt())
        val ah = max(16, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, aw, ah, true)
        val px = IntArray(aw * ah)
        small.getPixels(px, 0, aw, 0, 0, aw, ah)
        if (small !== src) small.recycle()

        val n = aw * ah
        val skin = BooleanArray(n)
        val dark = BooleanArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            // Warm, moderately saturated band: covers pale-to-dark anime skin.
            // Rejects white/grey (r-b < 6), vivid hair/clothes (r-b > 130 or
            // spread > 135) and anything cooler than skin (b-dominant).
            skin[i] = r > 110 && b > 50 && r >= g && g >= b - 12 &&
                (r - b) >= 6 && (r - b) <= 130 && (mx - mn) <= 135
            val luma = (r * 77 + g * 150 + b * 29) shr 8
            dark[i] = luma < 80
        }

        // Connected skin blobs — iterative 4-neighbour flood fill (no recursion,
        // fixed arrays; the analysis bitmap is at most 160x160 = 25.6k pixels).
        val visited = BooleanArray(n)
        val stack = IntArray(n)
        var bestScore = 0f
        var bestRect: RectF? = null
        for (start in 0 until n) {
            if (!skin[start] || visited[start]) continue
            var top = ah; var bottom = 0; var left = aw; var right = 0; var size = 0
            var sp = 0
            stack[sp++] = start
            visited[start] = true
            while (sp > 0) {
                val p = stack[--sp]
                size++
                val y = p / aw
                val x = p % aw
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x < left) left = x
                if (x > right) right = x
                if (x > 0) { val q = p - 1; if (skin[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (x < aw - 1) { val q = p + 1; if (skin[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (y > 0) { val q = p - aw; if (skin[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
                if (y < ah - 1) { val q = p + aw; if (skin[q] && !visited[q]) { visited[q] = true; stack[sp++] = q } }
            }
            val bw = right - left + 1
            val bh = bottom - top + 1
            if (size < n / 120) continue                    // < ~0.8% of pixels → noise
            val density = size / (bw * bh).toFloat()
            if (density < 0.34f) continue                    // scattered / hollow → not a face
            val aspect = bw / bh.toFloat()
            if (aspect < 0.35f || aspect > 2.1f) continue    // elongated → arm / leg / torso
            // Eye evidence: dark pixels (eyes, brows, lashes) in the blob's
            // upper 70%. Hands, necks and shoulders have none.
            var eyeDark = 0
            val eyeBottom = top + (bh * 7) / 10
            for (yy in top..eyeBottom) {
                val row = yy * aw
                for (xx in left..right) if (dark[row + xx]) eyeDark++
            }
            val eyeFrac = eyeDark / (bw * bh).toFloat()
            if (eyeFrac < 0.02f) continue
            val cyBlob = (top + bottom) / 2f
            // Positional plausibility (the "cropped-to-abs" bug: a bare torso
            // is a big compact skin blob with dark shading lines that passed
            // every gate above). A face is either high in the frame or, when
            // low-centered, dominates it (close-up portrait). A mid-frame
            // blob that ISN'T huge is a torso/limb — never a face.
            if (cyBlob > ah * 0.55f) continue
            if (top > ah * 0.35f && bh < ah * 0.40f) continue
            val cy = cyBlob / ah                             // 0 = top of image
            val posWeight = 1.6f - cy                        // faces live high in character art
            val score = size * density * posWeight * (1f + min(eyeFrac * 8f, 1f))
            if (score > bestScore) {
                bestScore = score
                bestRect = RectF(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
            }
        }
        val rect = bestRect ?: return null
        // The skin box stops at the forehead — extend upward for the hair.
        val bh = rect.height()
        rect.top -= bh * 0.45f
        rect.bottom += bh * 0.08f
        val inv = 1f / scale
        return RectF(rect.left * inv, rect.top * inv, rect.right * inv, rect.bottom * inv)
    }

    // ---- framing ---------------------------------------------------------

    /**
     * Builds the 3:4 passport frame around [face]: the face fills roughly
     * half the frame height with a little headroom, clamped to the image.
     * Null when the face is too small to trust (< 8% of image height) or the
     * frame would be ≈ the whole image anyway (nothing to gain from a crop).
     */
    private fun passportFrame(face: RectF, w: Int, h: Int): Rect? {
        val faceH = face.height()
        if (faceH < h * 0.08f) return null
        var frameH = faceH * 1.9f
        var frameW = frameH * ASPECT
        val fit = min(1f, min(w / frameW, h / frameH))
        frameW *= fit
        frameH *= fit
        val left = (face.centerX() - frameW / 2f).coerceIn(0f, max(0f, w - frameW))
        val top = (face.top - 0.10f * frameH).coerceIn(0f, max(0f, h - frameH))
        val r = Rect(
            left.toInt(),
            top.toInt(),
            min(w, (left + frameW).toInt()),
            min(h, (top + frameH).toInt()),
        )
        if (r.width() < 32 || r.height() < 32) return null
        if (r.width().toLong() * r.height() >= w.toLong() * h * 92L / 100L) return null
        return r
    }
}
