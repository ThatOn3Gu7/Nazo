package quiz.thaton3app.nazo.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File

/**
 * "Share result" card: renders a themed 1080×1350 PNG of the run's numbers
 * with plain android.graphics (deterministic, no Compose capture, no
 * third-party libs), saves it under cacheDir/share/ and fires the system
 * share sheet via the existing FileProvider.
 *
 * Colors are passed in from the composables (Nazo* accessors), so the card
 * always matches the active accent + light/dark mode.
 */
object ShareResultCard {

    fun share(
        context: Context,
        heading: String,        // e.g. "Quiz Complete"
        headline: String,       // the big number, e.g. "80%"
        headlineCaption: String,// under the big number, e.g. "accuracy"
        stats: List<Pair<String, String>>, // label → value rows
        background: Color,
        surface: Color,
        primary: Color,
        onPrimary: Color,
        textPrimary: Color,
        textSecondary: Color,
    ) {
        runCatching {
            val w = 1080
            val h = 1350
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Backdrop
            paint.color = background.toArgb()
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

            // Soft accent orbs in the corners (echoes the ambient background)
            paint.color = primary.copy(alpha = 0.10f).toArgb()
            c.drawCircle(w * 0.92f, h * 0.06f, 260f, paint)
            c.drawCircle(w * 0.06f, h * 0.94f, 300f, paint)

            // 謎 mark, top-left
            paint.color = primary.copy(alpha = 0.85f).toArgb()
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 110f
            paint.textAlign = Paint.Align.LEFT
            c.drawText("謎", 72f, 176f, paint)
            paint.color = textSecondary.toArgb()
            paint.textSize = 44f
            c.drawText("NAZO — ANIME QUIZ", 210f, 156f, paint)

            // Card
            paint.color = surface.toArgb()
            val card = RectF(72f, 260f, w - 72f, h - 220f)
            c.drawRoundRect(card, 64f, 64f, paint)

            // Heading
            paint.color = textPrimary.toArgb()
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            c.drawText(heading, w / 2f, card.top + 140f, paint)

            // Big headline circle
            val cy = card.top + 420f
            paint.color = primary.toArgb()
            c.drawCircle(w / 2f, cy, 190f, paint)
            paint.color = onPrimary.toArgb()
            paint.textSize = 120f
            c.drawText(headline, w / 2f, cy + 12f, paint)
            paint.textSize = 44f
            c.drawText(headlineCaption, w / 2f, cy + 92f, paint)

            // Stat rows
            paint.typeface = Typeface.DEFAULT
            var y = cy + 330f
            stats.forEach { (label, value) ->
                paint.textAlign = Paint.Align.LEFT
                paint.color = textSecondary.toArgb()
                paint.textSize = 46f
                c.drawText(label, card.left + 90f, y, paint)
                paint.textAlign = Paint.Align.RIGHT
                paint.color = textPrimary.toArgb()
                paint.typeface = Typeface.DEFAULT_BOLD
                c.drawText(value, card.right - 90f, y, paint)
                paint.typeface = Typeface.DEFAULT
                y += 96f
            }

            // Footer
            paint.textAlign = Paint.Align.CENTER
            paint.color = textSecondary.copy(alpha = 0.7f).toArgb()
            paint.textSize = 40f
            c.drawText("Can you beat me? • Nazo Anime Quiz", w / 2f, h - 110f, paint)

            // Save + share via the existing FileProvider (cache-path "share")
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "nazo_result.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()

            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share your result"))
        }
    }
}
