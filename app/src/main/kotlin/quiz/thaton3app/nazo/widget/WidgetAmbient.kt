package quiz.thaton3app.nazo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.toArgb
import quiz.thaton3app.nazo.ui.theme.resolveAccent

/**
 * Draws a STATIC version of the app's ambient background, sized for one
 * particular widget.
 *
 * ## Why a bitmap and not a drawable or an animation
 *
 * RemoteViews can host neither Compose nor a custom View, so
 * [quiz.thaton3app.nazo.ui.components.AmbientBackground]'s live canvas cannot be
 * reused. The previous attempt cross-faded three hand-written layer-list
 * drawables in a ViewFlipper. That had two problems:
 *
 *  - it read as a low-frame-rate slideshow rather than as motion, and
 *  - a layer-list positions every element at a fixed dp offset, so resizing the
 *    widget just stretched the same artwork.
 *
 * This renders the particles ourselves at the widget's real pixel size, once
 * per update, and hands the result over as a plain ImageView bitmap. There is
 * no timer, no service and no alarm: the widget is completely idle between the
 * updates it was already doing.
 *
 * ## Determinism
 *
 * Every layout is generated from a [Random] seeded with the widget id, the
 * style and the cell size. The same widget at the same size therefore produces
 * a pixel-identical background on every refresh — it never jumps around when
 * the streak text changes. Resizing intentionally reseeds, because the point of
 * the resize is to lay the particles out again: counts, sizes and spacing are
 * all derived from the new area rather than scaled from the old one.
 */
internal object WidgetAmbient {

    /**
     * Widget bitmaps travel to the launcher through a Binder transaction whose
     * total budget is about 1 MB. Cap the longest edge so a very large widget
     * on a high-density screen cannot blow it; ImageView scales the result up,
     * and since the art is soft gradients and small dots the difference is not
     * visible.
     */
    private const val MAX_EDGE_PX = 1000

    /** Corner radius of the widget card, matching res/drawable/widget_bg.xml. */
    private const val CORNER_DP = 24f

    /**
     * Build the background for one widget.
     *
     * [widthDp]/[heightDp] come from the host's own option bundle, so the art
     * matches what the launcher actually allotted. Returns null if the size is
     * not sensible yet (some hosts report zero before the first layout), in
     * which case the caller should fall back to the plain card background.
     */
    fun render(
        context: Context,
        widgetId: Int,
        style: String,
        accentId: String,
        dark: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap? {
        if (widthDp <= 0 || heightDp <= 0) return null

        val density = context.resources.displayMetrics.density
        val fullW = (widthDp * density).roundToInt()
        val fullH = (heightDp * density).roundToInt()
        if (fullW <= 0 || fullH <= 0) return null
        // Downscale factor if the widget is larger than the Binder budget allows.
        val scale = min(1f, MAX_EDGE_PX.toFloat() / max(fullW, fullH))
        val w = max(1, (fullW * scale).roundToInt())
        val h = max(1, (fullH * scale).roundToInt())

        val accent = runCatching { resolveAccent(accentId, dark).primary.toArgb() }
            .getOrDefault(Color.parseColor("#2E8560"))

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Card fill + border first; the particles are then clipped to it so
        // nothing bleeds past the rounded corners.
        // Radius in bitmap pixels: the same 24dp card corner, shrunk by the
        // same factor as the bitmap so it still lines up once ImageView scales
        // the result back up to the widget's real size.
        val radius = CORNER_DP * density * scale
        drawCard(canvas, w.toFloat(), h.toFloat(), radius, dark)

        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)

        // Seeded on id + style + size: stable across refreshes, relaid out on resize.
        val pxPerDp = density * scale
        val rng = Random(("$widgetId|$style|$w|$h").hashCode())
        when (style) {
            "constellation" -> drawConstellation(canvas, w.toFloat(), h.toFloat(), pxPerDp, accent, rng)
            "rain" -> drawRain(canvas, w.toFloat(), h.toFloat(), pxPerDp, accent, rng)
            "orbs" -> drawOrbs(canvas, w.toFloat(), h.toFloat(), pxPerDp, accent, rng)
            else -> drawShapes(canvas, w.toFloat(), h.toFloat(), pxPerDp, accent, rng)
        }

        canvas.restore()
        return bitmap
    }

    private fun drawCard(canvas: Canvas, w: Float, h: Float, radius: Float, dark: Boolean) {
        val rect = RectF(0f, 0f, w, h)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                if (dark) Color.parseColor("#1A2620") else Color.parseColor("#16211C"),
                if (dark) Color.parseColor("#101A15") else Color.parseColor("#0E1712"),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, fill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = max(1f, radius * 0.045f)
            color = Color.parseColor("#2E4237")
        }
        val inset = border.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            radius - inset, radius - inset, border,
        )
    }

    /**
     * Particle count scales with AREA, not with a fixed number, so a wide 4x2
     * widget is not sparser than a small 3x1 one. [per10kDp2] is how many
     * particles a 100dp x 100dp patch should hold.
     */
    private fun countFor(w: Float, h: Float, pxPerDp: Float, per10kDp2: Float, lo: Int, hi: Int): Int {
        val patch = 100f * pxPerDp
        val cells = (w * h) / (patch * patch)
        return (cells * per10kDp2).roundToInt().coerceIn(lo, hi)
    }

    private fun alpha(color: Int, a: Float): Int =
        Color.argb((a * 255).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    // -- shapes ------------------------------------------------------------

    /** Small outlined polygons, echoing the app's "particles" style. */
    private fun drawShapes(canvas: Canvas, w: Float, h: Float, pxPerDp: Float, accent: Int, rng: Random) {
        val unit = min(w, h)
        val n = countFor(w, h, pxPerDp, 2.2f, 4, 26)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        repeat(n) {
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h
            // Size follows the widget's short edge so shapes stay proportionate.
            val r = unit * (0.045f + rng.nextFloat() * 0.055f)
            paint.strokeWidth = max(1.2f, r * 0.14f)
            paint.color = alpha(accent, 0.16f + rng.nextFloat() * 0.16f)
            val sides = 3 + rng.nextInt(4)
            val rotation = rng.nextFloat() * 2f * PI.toFloat()
            if (sides == 6) {
                canvas.drawCircle(cx, cy, r * 0.85f, paint)
            } else {
                canvas.drawPath(polygon(cx, cy, r, sides, rotation), paint)
            }
        }
    }

    private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotation: Float): Path =
        Path().apply {
            for (i in 0 until sides) {
                val a = rotation + i * 2f * PI.toFloat() / sides
                val px = cx + r * cos(a)
                val py = cy + r * sin(a)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }

    // -- constellation -----------------------------------------------------

    /** Dots plus links between near neighbours; the link radius scales with size. */
    private fun drawConstellation(canvas: Canvas, w: Float, h: Float, pxPerDp: Float, accent: Int, rng: Random) {
        val unit = min(w, h)
        val n = countFor(w, h, pxPerDp, 5.5f, 6, 48)
        val xs = FloatArray(n) { rng.nextFloat() * w }
        val ys = FloatArray(n) { rng.nextFloat() * h }
        val rs = FloatArray(n) { unit * (0.012f + rng.nextFloat() * 0.014f) }

        // Link distance is a fraction of the diagonal, so the web keeps the same
        // visual density whatever the widget's aspect ratio.
        val linkDist = hypot(w, h) * 0.22f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1f, unit * 0.006f)
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val d = hypot(xs[i] - xs[j], ys[i] - ys[j])
                if (d > linkDist) continue
                line.color = alpha(accent, 0.20f * (1f - d / linkDist))
                canvas.drawLine(xs[i], ys[i], xs[j], ys[j], line)
            }
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until n) {
            dot.color = alpha(accent, 0.30f + rng.nextFloat() * 0.30f)
            canvas.drawCircle(xs[i], ys[i], rs[i], dot)
        }
    }

    // -- rain --------------------------------------------------------------

    /** Vertical streaks; length scales with height, count with width. */
    private fun drawRain(canvas: Canvas, w: Float, h: Float, pxPerDp: Float, accent: Int, rng: Random) {
        val n = countFor(w, h, pxPerDp, 6f, 6, 44)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = max(1f, min(w, h) * 0.010f)
        }
        repeat(n) {
            val x = rng.nextFloat() * w
            val len = h * (0.14f + rng.nextFloat() * 0.26f)
            val y = rng.nextFloat() * (h - len)
            paint.color = alpha(accent, 0.12f + rng.nextFloat() * 0.20f)
            canvas.drawLine(x, y, x, y + len, paint)
        }
    }

    // -- orbs --------------------------------------------------------------

    /** A few big soft radial glows; radius is a fraction of the short edge. */
    private fun drawOrbs(canvas: Canvas, w: Float, h: Float, pxPerDp: Float, accent: Int, rng: Random) {
        val unit = min(w, h)
        val n = countFor(w, h, pxPerDp, 1.1f, 2, 9)
        repeat(n) {
            val cx = rng.nextFloat() * w
            val cy = rng.nextFloat() * h
            val r = unit * (0.28f + rng.nextFloat() * 0.34f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, r,
                    alpha(accent, 0.24f),
                    alpha(accent, 0f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    @Suppress("unused")
    private fun dp(context: Context, value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics,
    )
}
