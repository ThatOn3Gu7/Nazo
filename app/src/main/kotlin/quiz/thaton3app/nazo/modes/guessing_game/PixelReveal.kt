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

/**
 * Decodes [bytes] into one pre-scaled bitmap per pixel level (nearest-neighbour
 * downscale, so drawing the small bitmap back up keeps crisp pixel edges —
 * no re-scaling work per frame). Returns null when the bytes don't decode, in
 * which case the caller falls back to the blur reveal.
 */
internal fun buildPixelLevels(bytes: ByteArray): List<Bitmap>? {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return PIXEL_LEVELS.map { scale ->
        Bitmap.createScaledBitmap(
            original,
            (original.width / scale).coerceAtLeast(1),
            (original.height / scale).coerceAtLeast(1),
            false, // nearest neighbour — crisp pixel edges
        )
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
