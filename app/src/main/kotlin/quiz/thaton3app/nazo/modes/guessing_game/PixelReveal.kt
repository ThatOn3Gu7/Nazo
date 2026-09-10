package quiz.thaton3app.nazo.modes.guessing_game

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * Decodes fetched image bytes into a predictable software bitmap.
 *
 * The important boundary here is the encoded bytes -> Android bitmap. Remote
 * sources can legitimately return different encoded formats and colour spaces.
 * The guessing renderer should never have to deal with a wide-gamut / floating
 * point bitmap or unusual colour-space metadata, so every image is decoded as
 * software pixels and normalized to premultiplied ARGB_8888 sRGB before draw.
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
    return normalizeMysteryBitmap(decoded)
}

/**
 * Converts any decoded bitmap into the exact representation expected by the
 * Compose/Canvas renderer: premultiplied ARGB_8888 in sRGB.
 *
 * This is deliberately a simple conversion boundary rather than another image
 * effect. Wide-gamut/F16/HDR-capable decodes, embedded colour profiles, and
 * other source-specific bitmap representations are flattened through the
 * Android Canvas into ordinary 8-bit sRGB pixels before the game draws them.
 * The source alpha channel is preserved.
 */
internal fun normalizeMysteryBitmap(source: Bitmap): Bitmap {
    val sRgb = ColorSpace.get(ColorSpace.Named.SRGB)
    val alreadyNormalized =
        source.config == Bitmap.Config.ARGB_8888 &&
            source.colorSpace?.isSrgb == true &&
            source.isPremultiplied
    if (alreadyNormalized) return source

    val normalized = Bitmap.createBitmap(
        source.width,
        source.height,
        Bitmap.Config.ARGB_8888,
        true,
        sRgb,
    )
    normalized.setPremultiplied(true)
    val canvas = Canvas(normalized)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, 0f, 0f, paint)
    return normalized
}

/**
 * Diagnostic/normal rendering path. The image is always normalized once before
 * Compose draws it, so the reveal code receives a stable pixel representation
 * regardless of which official-art CDN supplied the encoded source.
 */
@Composable
internal fun PixelatedImage(
    bitmap: Bitmap,
    @Suppress("UNUSED_PARAMETER") cellSize: Int,
    modifier: Modifier,
) {
    val displayBitmap = remember(bitmap) { normalizeMysteryBitmap(bitmap) }

    DisposableEffect(displayBitmap, bitmap) {
        onDispose {
            if (displayBitmap !== bitmap && !displayBitmap.isRecycled) {
                displayBitmap.recycle()
            }
        }
    }

    Image(
        bitmap = displayBitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Medium,
    )
}
