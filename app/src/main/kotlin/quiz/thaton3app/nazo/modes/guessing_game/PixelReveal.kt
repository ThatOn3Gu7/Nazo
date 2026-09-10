package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Pixel-cell size (in source pixels) for each reveal step — index 0 is fully
 * sharp. Coarse at the deep end (a heavily obscured round start), fine near
 * the reveal, so the un-pixelating stays visible the whole round.
 */
internal val PIXEL_LEVELS = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128)

/** Longest edge the source image is decoded at (Phase 6, low-RAM cap). The
 * card renders at ~300dp, so ~1600px keeps it visually lossless while a
 * 4000px camera-grade fetch drops from ~48MB of ARGB to ~10MB. */
private const val MAX_DECODE_DIM = 1600

/**
 * Decodes [bytes] into one pre-scaled bitmap per pixel level (nearest-neighbour
 * downscale, so drawing the small bitmap back up keeps crisp pixel edges —
 * no re-scaling work per frame). Returns null when the bytes don't decode, in
 * which case the caller falls back to the blur reveal.
 *
 * Memory-hardened for 4GB devices: a bounds-only first pass computes a
 * power-of-two inSampleSize so the full-resolution image is NEVER held in
 * memory, and the sharp level-0 bitmap reuses the decoded bitmap directly.
 */
internal fun buildPixelLevels(bytes: ByteArray): List<Bitmap>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIM) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    // Flatten any alpha channel onto opaque white FIRST.
    //
    // Character art often ships as a transparent PNG, and BitmapFactory decodes
    // premultiplied (stored RGB = trueRGB * alpha). Bilinear downscaling such a
    // bitmap averages premultiplied colour against fully transparent
    // (0,0,0,0) neighbours, which drags every partially-transparent region
    // toward white and blows out the picture. Compositing onto white up front
    // means every level below is built from honest, opaque colour.
    //
    // This path matters when auto-crop is OFF (PortraitCrop does its own
    // flattening); with it ON the bytes arriving here are already opaque and
    // this is a cheap no-op.
    val original = flattenOntoWhite(decoded)
    if (original !== decoded) decoded.recycle()
    return PIXEL_LEVELS.map { scale ->
        if (scale == 1) {
            original
        } else {
            Bitmap.createScaledBitmap(
                original,
                (original.width / scale).coerceAtLeast(1),
                (original.height / scale).coerceAtLeast(1),
                // filter = true (bilinear).
                //
                // This call scales DOWN, by up to 128x. Nearest neighbour keeps
                // one arbitrary source pixel per cell and discards the rest, so
                // each block took the colour of whatever pixel happened to land
                // on the sample grid and fine detail aliased into blotchy,
                // slightly wrong colours. Averaging the pixels a block covers
                // gives it the true mean colour of that region.
                //
                // The reveal still reads as hard-edged pixel art because the
                // crisp edges come from the UPSCALE in PixelatedImage
                // (FilterQuality.None), not from this downscale.
                //
                // NOTE: this is a quality improvement, not the fix for the
                // "corrupted image" report — that was a use-after-free on these
                // bitmaps; see the comment in GuessingPlayScreen where the
                // recycling used to happen.
                true,
            )
        }
    }
}

/**
 * Returns an opaque copy of [src] composited over white, or [src] itself when
 * it is already opaque. Drawing onto an opaque canvas is the only safe way to
 * resolve premultiplied alpha; reading the RGB out directly keeps the
 * premultiplied values.
 */
private fun flattenOntoWhite(src: Bitmap): Bitmap {
    if (!src.hasAlpha()) return src
    val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(flat)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    flat.setHasAlpha(false)
    return flat
}

/**
 * Draws [levels][levelIndex] upscaled with nearest-neighbour sampling and
 * centre-cropped to fill the canvas — the "un-pixelating" mystery image.
 */
@Composable
internal fun PixelatedImage(
    levels: List<Bitmap>,
    levelIndex: Int,
    modifier: Modifier,
) {
    val bitmap = levels[levelIndex.coerceIn(0, levels.size - 1)]
    Canvas(modifier = modifier) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        // Centre-crop: scale to cover the canvas, then centre the overflow.
        val coverScale = maxOf(size.width / srcW, size.height / srcH)
        val drawW = (srcW * coverScale).roundToInt()
        val drawH = (srcH * coverScale).roundToInt()
        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(
                ((size.width - drawW) / 2f).roundToInt(),
                ((size.height - drawH) / 2f).roundToInt(),
            ),
            dstSize = IntSize(drawW, drawH),
            filterQuality = FilterQuality.None, // nearest neighbour → crisp pixels
        )
    }
}
