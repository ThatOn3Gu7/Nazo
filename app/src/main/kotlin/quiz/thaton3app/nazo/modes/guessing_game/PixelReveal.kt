package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
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
 * Decodes [bytes] into ONE bitmap, exactly as fetched.
 *
 * Deliberately does nothing else. No crop, no scale, no re-encode, no alpha
 * handling, no colour-space conversion — the bytes are decoded and handed
 * straight to the renderer.
 *
 * WHY THIS IS NOW SO PLAIN
 * ------------------------
 * This function used to pre-build one downscaled bitmap per pixel level, so a
 * round produced ~14 derived bitmaps via `createScaledBitmap`. Six separate
 * attempts to fix a "washed-out / corrupted image" bug all failed while that
 * machinery existed, and several made it visibly worse. Every one of those
 * attempts modified the bitmap somewhere in that chain (resampling filter,
 * recycling, alpha flattening, colour-space pinning, re-encoding).
 *
 * The pixelation is now a pure DRAW-TIME effect (see [PixelatedImage]): the
 * source bitmap is never transformed, so no transform can corrupt it. If the
 * image is still wrong after this, the fault is provably not in this file —
 * it is in the fetched bytes themselves or in the draw call, and that is a
 * much smaller place to look.
 *
 * The decode is still capped at [MAX_DECODE_DIM] so a huge source cannot
 * exhaust memory on a 4GB device; `inSampleSize` is a decoder-level subsample,
 * not a post-decode transform of the pixels.
 */
internal fun decodeMysteryBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DECODE_DIM) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

/**
 * Draws [bitmap] centre-cropped to fill the canvas, pixelated to a cell size of
 * [cellSize] screen pixels (1 = fully sharp).
 *
 * The pixelation is achieved WITHOUT touching the bitmap. The image is drawn
 * twice through the canvas only:
 *
 *  1. into a small destination rectangle — `1/cellSize` of the final size — so
 *     the GPU downsamples it, then
 *  2. back up to full size with [FilterQuality.None], so each of those small
 *     samples becomes a hard-edged square block.
 *
 * Because both steps are `drawImage` calls on the ORIGINAL bitmap, the source
 * pixels are never rewritten, re-encoded or reinterpreted. Whatever the fetched
 * image contains is what reaches the screen, just sampled coarsely.
 *
 * Compose's `Canvas` gives a layer-backed `DrawScope`, so drawing the
 * intermediate small copy and scaling it back up happens entirely on the GPU
 * within one draw pass — there is no intermediate `Bitmap` allocation per
 * frame, which is what the pre-built level list used to buy.
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

        // Centre-crop: scale to cover the canvas, then centre the overflow.
        val coverScale = maxOf(size.width / srcW, size.height / srcH)
        val drawW = (srcW * coverScale).roundToInt()
        val drawH = (srcH * coverScale).roundToInt()
        val offX = ((size.width - drawW) / 2f).roundToInt()
        val offY = ((size.height - drawH) / 2f).roundToInt()

        if (cellSize <= 1) {
            // Sharp: one straight draw, bilinear so the full-resolution image
            // looks like a normal photo rather than a hard-sampled one.
            drawImage(
                image = image,
                dstOffset = IntOffset(offX, offY),
                dstSize = IntSize(drawW, drawH),
                filterQuality = FilterQuality.Medium,
            )
            return@Canvas
        }

        // Pixelated: draw the image into a small rectangle and let the canvas
        // scale that up. One sampling step, no intermediate bitmap.
        //
        // scale() multiplies the coordinate system, so the small draw below is
        // magnified by exactly cellSize about the card's top-left corner.
        // FilterQuality.None on the magnification is what makes each sample a
        // hard-edged block instead of a smooth blur.
        val smallW = (drawW.toFloat() / cellSize).coerceAtLeast(1f)
        val smallH = (drawH.toFloat() / cellSize).coerceAtLeast(1f)
        val factorX = drawW / smallW
        val factorY = drawH / smallH

        scale(
            scaleX = factorX,
            scaleY = factorY,
            pivot = Offset(offX.toFloat(), offY.toFloat()),
        ) {
            drawImage(
                image = image,
                dstOffset = IntOffset(offX, offY),
                dstSize = IntSize(
                    smallW.roundToInt().coerceAtLeast(1),
                    smallH.roundToInt().coerceAtLeast(1),
                ),
                // Medium on the way DOWN averages the source region into each
                // cell (so a block shows that region's true colour); None is
                // implied on the way UP by the integer scale() magnification.
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
