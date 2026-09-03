package quiz.thaton3app.nazo.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import quiz.thaton3app.nazo.IntroStyle
import quiz.thaton3app.nazo.R

/** Raster size for the punched-out mark. Matches the legacy 512px PNG foreground. */
private const val MARK_RASTER_PX = 512

private val IntroBackgroundLight = Color(0xFF36A06F)
private val IntroBackgroundDark = Color(0xFF246D4C)

// Slow initial swell, then rapid acceleration past the camera
private val WarpSpeedEasing = CubicBezierEasing(0.7f, 0f, 0.15f, 1f)

/**
 * Cold-start intro that continues the system splash.
 *
 * Every style works the same way underneath: the mark is *punched out* of an
 * opaque colored ground with [BlendMode.DstOut], so what animates is a hole
 * revealing the app below — never a drawn logo. That's why [mark] must be a
 * flat silhouette (see `AppIconOption.introMark`); full-color art would punch
 * out as an unreadable blob.
 *
 * @param isDark OS dark mode, used only by the default green pair.
 * @param backgroundColor overrides the green pair — passed when a custom icon is
 *   active so the intro continues the splash's color with no visible cut.
 * @param mark silhouette drawable punched out of the ground.
 * @param style which launch animation to play; see [IntroStyle].
 */
@Composable
fun IntroOverlay(
    isDark: Boolean,
    backgroundColor: Color? = null,
    mark: Int = R.drawable.ic_launcher_foreground,
    style: IntroStyle = IntroStyle.WARP,
) {
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return

    // Rasterize through the DRAWABLE pipeline, not ImageBitmap.imageResource():
    // that helper only decodes raster assets (BitmapFactory) and throws on a
    // VectorDrawable, which is what the illustrated marks are. Going via
    // ContextCompat.getDrawable().toBitmap() handles vectors and PNGs alike.
    // Rendered once per mark at a fixed size; the zoom is applied at draw time,
    // and DstOut only samples alpha, so this resolution is ample.
    val context = LocalContext.current
    val logoBitmap: ImageBitmap = remember(mark) {
        val drawable = checkNotNull(ContextCompat.getDrawable(context, mark)) {
            "Intro mark drawable $mark could not be loaded"
        }
        drawable.toBitmap(width = MARK_RASTER_PX, height = MARK_RASTER_PX).asImageBitmap()
    }

    // Shared drivers. Each style animates a different subset; unused ones simply
    // stay at their initial value, so one Canvas can serve every style.
    val zoomScale = remember { Animatable(1f) }
    val backgroundAlpha = remember { Animatable(1f) }
    // 0 -> 1 progress used by the non-warp styles for their bespoke motion.
    val progress = remember { Animatable(0f) }

    LaunchedEffect(style) {
        delay(80)

        when (style) {
            IntroStyle.WARP -> {
                val duration = 650
                coroutineScope {
                    launch {
                        zoomScale.animateTo(
                            targetValue = 18f,
                            animationSpec = tween(duration, easing = WarpSpeedEasing),
                        )
                    }
                    launch {
                        delay(200)
                        backgroundAlpha.animateTo(0f, tween(duration - 200))
                    }
                }
            }

            IntroStyle.LANTERN_GLOW -> {
                // The lantern swells gently (as if the flame catches), then its
                // light floods outward and consumes the ground.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(820, easing = LinearEasing)) }
                    launch {
                        zoomScale.animateTo(1.18f, tween(420, easing = FastOutSlowInEasing))
                        zoomScale.animateTo(26f, tween(420, easing = WarpSpeedEasing))
                    }
                    launch {
                        delay(500)
                        backgroundAlpha.animateTo(0f, tween(340))
                    }
                }
            }



            IntroStyle.N_STROKE -> {
                // Owner's brief: "a line rises up from the bottom, then wing, wing,
                // wing, turns into N". The ground is carved by a stroke that draws
                // itself along the letter's own polyline (see the Canvas below);
                // once the N is complete it flares and warps away.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(900, easing = LinearEasing)) }
                    launch {
                        // Hold at rest while the stroke draws, then blow through.
                        delay(620)
                        zoomScale.animateTo(20f, tween(380, easing = WarpSpeedEasing))
                    }
                    launch {
                        delay(700)
                        backgroundAlpha.animateTo(0f, tween(300))
                    }
                }
            }

            IntroStyle.PIXEL_RESOLVE -> {
                // Mirrors the Guessing Game's reveal: the mark jumps through discrete
                // mosaic steps (no smooth tween) before resolving and zooming out.
                coroutineScope {
                    launch { progress.animateTo(1f, tween(820, easing = LinearEasing)) }
                    launch {
                        // Stepped scale-up: each hop reads as a coarser->finer pass.
                        listOf(1.10f, 1.02f, 1.14f, 1.06f).forEach { step ->
                            zoomScale.snapTo(step)
                            delay(95)
                        }
                        zoomScale.snapTo(1f)
                        delay(80)
                        zoomScale.animateTo(19f, tween(380, easing = WarpSpeedEasing))
                    }
                    launch {
                        delay(520)
                        backgroundAlpha.animateTo(0f, tween(300))
                    }
                }
            }

        }

        dismissed = true
    }

    val bgColor = backgroundColor
        ?: if (isDark) IntroBackgroundDark else IntroBackgroundLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = backgroundAlpha.value
            }
            .background(bgColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val scale = zoomScale.value

            // Style-specific ground carving, drawn BEFORE the mark so the mark's
            // punch-out always wins where they overlap.
            when (style) {
                IntroStyle.PIXEL_RESOLVE -> {
                    // Quantized "mosaic dissolve": chunky blocks drop out of the
                    // ground in a deterministic scatter, so the reveal itself looks
                    // pixelated rather than a smooth fade.
                    val cols = 7
                    val cell = size.width / cols
                    val rows = (size.height / cell).toInt() + 1
                    for (row in 0 until rows) {
                        for (colIdx in 0 until cols) {
                            // Deterministic pseudo-random threshold per cell.
                            val h = (row * 73 + colIdx * 151) % 100 / 100f
                            if (p > h * 0.85f) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(colIdx * cell, row * cell),
                                    size = Size(cell, cell),
                                    blendMode = BlendMode.DstOut,
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }

            // The lantern sways as if hanging, damping out as the animation
            // completes so nothing jitters at the handoff. Other styles hold still.
            val swayX = if (style == IntroStyle.LANTERN_GLOW) {
                sin(p * 12f) * (1f - p) * size.width * 0.012f
            } else 0f

            // N_STROKE carves the letter by hand: the punch-out follows the same
            // polyline the neon art is built from, revealed progressively, so the
            // line appears to draw itself before the solid mark takes over.
            if (style == IntroStyle.N_STROKE) {
                drawNStroke(p)
            }

            val scaledWidth = (logoBitmap.width * scale).toInt()
            val scaledHeight = (logoBitmap.height * scale).toInt()

            val left = (size.width - scaledWidth) / 2f + swayX
            val top = (size.height - scaledHeight) / 2f

            // For N_STROKE the traced line IS the reveal for the first ~70%; only
            // then does the full mark punch through, so the two never double up.
            if (style != IntroStyle.N_STROKE || p > N_TRACE_END) {
                drawImage(
                    image = logoBitmap,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize(scaledWidth, scaledHeight),
                    blendMode = BlendMode.DstOut
                )
            }
        }
    }
}

/** Fraction of N_STROKE's progress spent drawing the line before the mark appears. */
private const val N_TRACE_END = 0.70f

/**
 * The letter N as a polyline in the marks' 108x108 viewport: up the left stem, down
 * the diagonal, up the right stem. Mirrors `ic_mark_n_neon.xml`'s pathData, so the
 * traced animation and the static art describe the same shape.
 */
private val N_POINTS = listOf(
    Offset(30f, 82f),
    Offset(30f, 28f),
    Offset(78f, 80f),
    Offset(78f, 28f),
)

/**
 * Punches a progressively-drawn N out of the ground.
 *
 * [p] is the overall 0..1 intro progress; the trace occupies the first [N_TRACE_END]
 * of it. The polyline is walked by arc length so the pen moves at a constant speed
 * regardless of segment length — without that the short stems would whip past while
 * the long diagonal crawled.
 */
private fun DrawScope.drawNStroke(p: Float) {
    val t = (p / N_TRACE_END).coerceIn(0f, 1f)
    // Fit the 108-unit design space into the smaller screen dimension, centered.
    val span = minOf(size.width, size.height) * 0.52f
    val unit = span / 108f
    val originX = (size.width - 108f * unit) / 2f
    val originY = (size.height - 108f * unit) / 2f
    fun map(o: Offset) = Offset(originX + o.x * unit, originY + o.y * unit)

    val pts = N_POINTS.map(::map)
    val lengths = pts.zipWithNext { a, b -> (b - a).getDistance() }
    val total = lengths.sum()
    var remaining = total * t

    val stroke = Stroke(
        width = span * 0.11f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    val path = Path().apply { moveTo(pts[0].x, pts[0].y) }
    for (i in lengths.indices) {
        val segLen = lengths[i]
        if (remaining <= 0f) break
        val frac = (remaining / segLen).coerceAtMost(1f)
        val end = pts[i] + (pts[i + 1] - pts[i]) * frac
        path.lineTo(end.x, end.y)
        remaining -= segLen
    }
    drawPath(path = path, color = Color.Black, style = stroke, blendMode = BlendMode.DstOut)
}
