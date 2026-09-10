package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return PIXEL_LEVELS.map { scale ->
        if (scale == 1) {
            original
        } else {
            Bitmap.createScaledBitmap(
                original,
                (original.width / scale).coerceAtLeast(1),
                (original.height / scale).coerceAtLeast(1),
                // filter = TRUE, and this is the important part.
                //
                // This used to pass false. Nearest neighbour is right when
                // scaling a small image UP (it keeps pixel edges crisp), but
                // this call scales DOWN — by up to 128x. Nearest neighbour
                // downscaling keeps ONE arbitrary source pixel per cell and
                // throws the other ~16,000 away, so each block took the colour
                // of whatever single pixel happened to land on the sample grid.
                // On detailed artwork neighbouring blocks then sampled unrelated
                // details and the reveal came out speckled and noisy, with
                // colours that did not match the picture.
                //
                // Bilinear filtering AVERAGES the pixels each block covers, so
                // a block shows the true mean colour of that region. The reveal
                // still looks like hard-edged pixel art because the upscale in
                // PixelatedImage draws with FilterQuality.None — crisp edges
                // come from the UPscale, not this downscale.
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
