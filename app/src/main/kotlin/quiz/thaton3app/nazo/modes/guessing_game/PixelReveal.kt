package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorSpace
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Pixel-cell size (in screen pixels) for each reveal step — index 0 is fully
 * sharp. Coarse at the deep end (a heavily obscured round start), fine near
 * the reveal, so the un-pixelating stays visible the whole round.
 */
internal val PIXEL_LEVELS = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128)

/** Longest edge the source image is decoded at (low-RAM cap). */
private const val MAX_DECODE_DIM = 1600

/**
 * Decodes fetched image bytes into a predictable opaque software bitmap.
 *
 * Two things happen here that matter:
 *
 * 1. **Colour-space pinning.** The decode is forced to ARGB_8888 / sRGB.
 *    Without this, BitmapFactory on Android 14+ may hand back RGBA_F16 or a
 *    wide-gamut colour space for HDR/Display-P3 sources, and the half-float
 *    values are not what `asImageBitmap()` + `Canvas.drawImage` assume —
 *    anything above 1.0 clamps to 0xFF, so midtones blow out to white.
 *
 * 2. **Alpha flattening.** Character art from Fandom and AniList is frequently
 *    a transparent-background PNG cutout. Android decodes those PREMULTIPLIED
 *    (`storedRGB = trueRGB * alpha`). When such a bitmap is scaled, cropped,
 *    or re-encoded, the premultiplied values are blended against transparent
 *    neighbours and the result washes to white. The only correct resolution
 *    is to composite onto an opaque background IMMEDIATELY after decoding,
 *    before any other operation touches the pixels.
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
        inPremultiplied = true
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return flattenAlpha(decoded)
}

/**
 * Composites [source] onto opaque white, returning a bitmap with no alpha.
 *
 * If the source is already opaque this is a no-op (returns [source] as-is).
 * Drawing onto an opaque Canvas is the one API that correctly resolves
 * premultiplied alpha — every other approach (getPixel, compress, manual
 * division) produces the washed-out corruption that plagued this pipeline.
 */
private fun flattenAlpha(source: Bitmap): Bitmap {
    if (!source.hasAlpha()) return source
    val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(flat).apply {
        drawColor(AndroidColor.WHITE)
        drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    source.recycle()
    flat.setHasAlpha(false)
    return flat
}

/**
 * Draws [bitmap] centre-cropped to fill [modifier], pixelated to the given
 * [cellSize] (1 = fully sharp, higher = coarser blocks).
 *
 * The pixelation works by downscaling the bitmap with bilinear filtering (so
 * each small pixel averages its source region) and then drawing the small
 * bitmap back up with [FilterQuality.None] (nearest neighbour), which gives
 * each block a hard edge. The bitmaps for each level are NOT recycled eagerly —
 * Compose's draw layer may still be referencing the previous frame's bitmap
 * during animation transitions. They become unreachable as soon as the round
 * state changes and the GC collects them.
 */
@Composable
internal fun PixelatedImage(
    bitmap: Bitmap,
    cellSize: Int,
    modifier: Modifier,
) {
    val displayBitmap = remember(bitmap, cellSize) {
        if (cellSize <= 1) {
            bitmap
        } else {
            val smallW = (bitmap.width / cellSize).coerceAtLeast(1)
            val smallH = (bitmap.height / cellSize).coerceAtLeast(1)
            // filter = true → bilinear downscale, so each block gets the true
            // average colour of the source region it covers.
            Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
        }
    }

    // Note: no DisposableEffect that calls recycle(). The animated reveal
    // changes cellSize many times per round, and Compose's draw layer holds
    // a reference to the previous-frame bitmap during the transition. Eagerly
    // recycling it causes a use-after-free (the yellow/purple speckle bug
    // from commit e9320c1). The small bitmaps are GC'd when the round ends.

    Image(
        bitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() },
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        // Nearest neighbour on the upscale is what makes the pixel blocks
        // hard-edged. When fully sharp, use bilinear for a clean photo look.
        filterQuality = if (cellSize <= 1) FilterQuality.Medium else FilterQuality.None,
    )
}
