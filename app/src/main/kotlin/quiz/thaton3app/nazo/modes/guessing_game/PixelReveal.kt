package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
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
 * TEMPORARY DIAGNOSTIC RENDERER.
 *
 * The old implementation used a custom Canvas path that drew the bitmap into
 * a tiny destination and then magnified it with a Canvas transform. That path
 * is now bypassed completely so we can isolate whether the corruption is in
 * Canvas/pixelation or in the decoded Bitmap itself.
 *
 * [cellSize] is intentionally ignored for this diagnostic build. The image is
 * rendered through Compose's normal Image path with the same Bitmap produced
 * by the existing Coil decode.
 */
@Composable
internal fun PixelatedImage(
    bitmap: Bitmap,
    @Suppress("UNUSED_PARAMETER") cellSize: Int,
    modifier: Modifier,
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Medium,
    )
}
