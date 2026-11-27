package quiz.thaton3app.nazo.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import quiz.thaton3app.nazo.R

/** Theme snapshot used while rendering away from the main thread. */
data class ShareResultCardPalette(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

/**
 * Exports a privacy-safe [ShareResultCardContent] as a polished 4:5 PNG and
 * opens Android's share sheet. Rendering and PNG compression happen on the IO
 * dispatcher so a large image never stalls the Results screen.
 */
object ShareResultCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val FILE_PREFIX = "nazo-result-"
    private const val MAX_CACHED_CARDS = 3

    suspend fun share(
        context: Context,
        content: ShareResultCardContent,
        palette: ShareResultCardPalette,
    ): Boolean {
        val file = withContext(Dispatchers.IO) {
            runCatching { renderToCache(context, content, palette) }.getOrNull()
        } ?: return false

        return withContext(Dispatchers.Main) {
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file,
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, content.shareText)
                    clipData = ClipData.newUri(context.contentResolver, "Nazo result", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Share your Nazo result")
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }.isSuccess
        }
    }

    private fun renderToCache(
        context: Context,
        content: ShareResultCardContent,
        palette: ShareResultCardPalette,
    ): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            render(context, Canvas(bitmap), content, palette)
            val directory = File(context.cacheDir, "share").apply { mkdirs() }
            require(directory.isDirectory) { "Could not create result-card cache" }
            pruneOldCards(directory)
            val file = File(directory, "$FILE_PREFIX${System.currentTimeMillis()}.png")
            file.outputStream().buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode result card"
                }
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(
        context: Context,
        canvas: Canvas,
        content: ShareResultCardContent,
        palette: ShareResultCardPalette,
    ) {
        val regular = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_regular)
            ?: Typeface.DEFAULT
        val medium = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_semibold)
            ?: Typeface.DEFAULT_BOLD
        val bold = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
            ?: Typeface.DEFAULT_BOLD
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

        paint.shader = LinearGradient(
            0f,
            0f,
            WIDTH.toFloat(),
            HEIGHT.toFloat(),
            palette.background.toArgb(),
            palette.surface.toArgb(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        paint.shader = null

        // Quiet ambient rings sit behind the actual card and echo Nazo's app
        // background without depending on motion or a captured screen.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 54f
        paint.color = palette.primary.copy(alpha = 0.10f).toArgb()
        canvas.drawCircle(1020f, 60f, 250f, paint)
        canvas.drawCircle(42f, 1310f, 280f, paint)
        paint.style = Paint.Style.FILL

        val card = RectF(56f, 48f, WIDTH - 56f, HEIGHT - 48f)
        paint.color = palette.textPrimary.copy(alpha = 0.10f).toArgb()
        canvas.drawRoundRect(card.left + 8f, card.top + 16f, card.right + 8f, card.bottom + 16f, 66f, 66f, paint)
        paint.color = palette.surface.toArgb()
        canvas.drawRoundRect(card, 66f, 66f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = palette.primary.copy(alpha = 0.18f).toArgb()
        canvas.drawRoundRect(card, 66f, 66f, paint)
        paint.style = Paint.Style.FILL

        canvas.save()
        val clip = Path().apply { addRoundRect(card, 66f, 66f, Path.Direction.CW) }
        canvas.clipPath(clip)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 40f
        paint.color = palette.primary.copy(alpha = 0.07f).toArgb()
        canvas.drawCircle(card.right + 15f, card.top - 5f, 155f, paint)
        canvas.drawCircle(card.right + 15f, card.top - 5f, 230f, paint)
        canvas.drawCircle(card.left - 20f, card.bottom + 25f, 180f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()

        // Brand lockup. The supplied mark is artwork, not user/profile media.
        paint.color = palette.primary.toArgb()
        canvas.drawRoundRect(104f, 96f, 220f, 212f, 34f, 34f, paint)
        val mark = BitmapFactory.decodeResource(context.resources, R.drawable.ic_mark_nazo_brand)
        if (mark != null) {
            canvas.drawBitmap(mark, null, RectF(126f, 118f, 198f, 190f), paint)
            mark.recycle()
        }
        paint.typeface = bold
        paint.color = palette.textPrimary.toArgb()
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 50f
        canvas.drawText("NAZO", 250f, 145f, paint)
        paint.typeface = medium
        paint.color = palette.textSecondary.toArgb()
        paint.textSize = 24f
        paint.letterSpacing = 0.12f
        canvas.drawText("ANIME QUIZ • RESULT CARD", 250f, 184f, paint)
        paint.letterSpacing = 0f

        // Fixed mode vocabulary keeps custom topics, names and answer content
        // out of the exported bitmap by construction.
        paint.typeface = bold
        paint.textAlign = Paint.Align.LEFT
        paint.color = palette.primary.toArgb()
        paint.textSize = 25f
        paint.letterSpacing = 0.13f
        canvas.drawText(content.eyebrow, 108f, 292f, paint)
        paint.letterSpacing = 0f
        paint.color = palette.textPrimary.toArgb()
        drawFittedText(
            canvas = canvas,
            paint = paint,
            text = content.heading,
            x = 108f,
            baseline = 374f,
            maxWidth = 864f,
            startSize = 66f,
            minSize = 44f,
            align = Paint.Align.LEFT,
        )

        val hero = RectF(104f, 430f, WIDTH - 104f, 765f)
        paint.color = palette.primary.toArgb()
        canvas.drawRoundRect(hero, 52f, 52f, paint)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(hero, 52f, 52f, Path.Direction.CW) })
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 28f
        paint.color = palette.onPrimary.copy(alpha = 0.10f).toArgb()
        canvas.drawCircle(hero.right - 8f, hero.top + 4f, 138f, paint)
        canvas.drawCircle(hero.left + 12f, hero.bottom + 18f, 120f, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()

        paint.typeface = bold
        paint.color = palette.onPrimary.toArgb()
        drawFittedText(
            canvas = canvas,
            paint = paint,
            text = content.headline,
            x = WIDTH / 2f,
            baseline = 615f,
            maxWidth = 680f,
            startSize = 148f,
            minSize = 92f,
            align = Paint.Align.CENTER,
        )
        paint.typeface = medium
        paint.textSize = 35f
        paint.textAlign = Paint.Align.CENTER
        paint.color = palette.onPrimary.copy(alpha = 0.82f).toArgb()
        canvas.drawText(content.headlineCaption.uppercase(), WIDTH / 2f, 675f, paint)

        content.highlight?.let { highlight ->
            paint.typeface = bold
            paint.textSize = 23f
            val horizontalPadding = 26f
            val chipWidth = paint.measureText(highlight) + horizontalPadding * 2f
            val chip = RectF(
                WIDTH / 2f - chipWidth / 2f,
                704f,
                WIDTH / 2f + chipWidth / 2f,
                748f,
            )
            paint.color = palette.onPrimary.copy(alpha = 0.16f).toArgb()
            canvas.drawRoundRect(chip, 22f, 22f, paint)
            paint.textAlign = Paint.Align.CENTER
            paint.color = palette.onPrimary.toArgb()
            canvas.drawText(highlight, WIDTH / 2f, 734f, paint)
        }

        val statsCard = RectF(104f, 808f, WIDTH - 104f, 1136f)
        paint.color = palette.background.copy(alpha = 0.72f).toArgb()
        canvas.drawRoundRect(statsCard, 44f, 44f, paint)
        content.stats.forEachIndexed { index, stat ->
            val rowTop = statsCard.top + index * 109f
            val baseline = rowTop + 68f
            if (index > 0) {
                paint.color = palette.textSecondary.copy(alpha = 0.16f).toArgb()
                canvas.drawRect(statsCard.left + 42f, rowTop, statsCard.right - 42f, rowTop + 2f, paint)
            }
            paint.typeface = regular
            paint.textSize = 31f
            paint.textAlign = Paint.Align.LEFT
            paint.color = palette.textSecondary.toArgb()
            canvas.drawText(stat.label, statsCard.left + 48f, baseline, paint)
            paint.typeface = bold
            paint.color = palette.textPrimary.toArgb()
            drawFittedText(
                canvas = canvas,
                paint = paint,
                text = stat.value,
                x = statsCard.right - 48f,
                baseline = baseline,
                maxWidth = 500f,
                startSize = 36f,
                minSize = 25f,
                align = Paint.Align.RIGHT,
            )
        }

        paint.typeface = bold
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 30f
        paint.color = palette.textPrimary.toArgb()
        canvas.drawText("PLAY • LEARN • MASTER", WIDTH / 2f, 1210f, paint)
        paint.typeface = regular
        paint.textSize = 23f
        paint.color = palette.textSecondary.toArgb()
        canvas.drawText("Shared from Nazo • No profile data included", WIDTH / 2f, 1252f, paint)
    }

    private fun drawFittedText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        startSize: Float,
        minSize: Float,
        align: Paint.Align,
    ) {
        paint.textAlign = align
        var size = startSize
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > minSize) {
            size -= 2f
            paint.textSize = size
        }
        canvas.drawText(text, x, baseline, paint)
    }

    private fun pruneOldCards(directory: File) {
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.extension == "png" }
            .sortedByDescending(File::lastModified)
            .drop(MAX_CACHED_CARDS - 1)
            .forEach(File::delete)
    }
}
