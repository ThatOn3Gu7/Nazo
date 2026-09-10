package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Pixel-cell size (in screen pixels) for each reveal step — index 0 is fully
 * sharp. Coarse at the deep end (a heavily obscured round start), fine near
 * the reveal, so the un-pixelating stays visible the whole round.
 */
internal val PIXEL_LEVELS = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128)

/** Longest edge the source image is decoded at (low-RAM cap). */
private const val MAX_DECODE_DIM = 1600

/**
 * Decodes [bytes] into ONE bitmap for rendering.
 *
 * No crop, scale, re-encode or bitmap mutation is performed after decoding.
 * The explicit ARGB_8888 + sRGB representation keeps the renderer on a known
 * 8-bit colour format instead of allowing a wide-gamut / higher-precision source
 * to reach a later stage with an incompatible pixel representation.
 */
internal fun decodeMysteryBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIM) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

/**
 * Draws [bitmap] centre-cropped to fill the canvas, pixelated to a cell size of
 * [cellSize] screen pixels (1 = fully sharp).
 *
 * Pixelation is draw-time only: the source bitmap is never modified. A small
 * destination draw is magnified with FilterQuality.None, producing hard-edged
 * blocks without allocating or rewriting intermediate bitmaps.
 */
@Composable
internal fun PixelatedImage(
    bitmap: Bitmap,
    cellSize: Int,
    modifier: Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(modifier = modifier) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        if (srcW <= 0f || srcH <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas

        val coverScale = maxOf(size.width / srcW, size.height / srcH)
        val drawW = (srcW * coverScale).roundToInt()
        val drawH = (srcH * coverScale).roundToInt()
        val offX = ((size.width - drawW) / 2f).roundToInt()
        val offY = ((size.height - drawH) / 2f).roundToInt()

        if (cellSize <= 1) {
            drawImage(
                image = image,
                dstOffset = IntOffset(offX, offY),
                dstSize = IntSize(drawW, drawH),
                filterQuality = FilterQuality.Medium,
            )
            return@Canvas
        }

        val cellsX = (drawW / cellSize).coerceAtLeast(1)
        val cellsY = (drawH / cellSize).coerceAtLeast(1)

        withTransform({
            scale(
                scaleX = cellSize.toFloat(),
                scaleY = cellSize.toFloat(),
                pivot = Offset(offX.toFloat(), offY.toFloat()),
            )
        }) {
            drawImage(
                image = image,
                dstOffset = IntOffset(offX, offY),
                dstSize = IntSize(cellsX, cellsY),
                filterQuality = FilterQuality.None,
            )
        }
    }
}
