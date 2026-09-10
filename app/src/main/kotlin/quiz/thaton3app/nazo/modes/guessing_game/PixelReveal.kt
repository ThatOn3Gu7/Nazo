package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import quiz.thaton3app.nazo.vision.ImageDiagnostics

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
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        // Pin the decode to 8-bit sRGB. Left to itself BitmapFactory may hand
        // back RGBA_F16 / a wide-gamut colour space for HDR or Display-P3
        // sources on Android 14, and those half-float values are not what
        // asImageBitmap() + Canvas.drawImage assume: anything above 1.0 clamps
        // to 0xFF, so midtones blow out to white and only near-black ink
        // survives. That is the reported corruption.
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    ImageDiagnostics.log("buildPixelLevels/decoded", bytes = bytes, bitmap = original)
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
