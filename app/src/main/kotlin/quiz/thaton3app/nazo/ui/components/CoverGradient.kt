package quiz.thaton3app.nazo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin
import quiz.thaton3app.nazo.data.profile.CoverPreset
import quiz.thaton3app.nazo.data.profile.CoverGradientStyle

private val CoverGradientProcessOriginNanos = System.nanoTime()

internal fun coverGradientTimelineSeconds(frameNanos: Long, originNanos: Long): Float =
    ((frameNanos - originNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat()

/**
 * Draws one built-in animated-gradient cover into the current draw scope.
 *
 * [CoverGradientStyle] selects a visual family, not just a palette:
 *
 * - [CoverGradientStyle.RIBBONS] is the original five-layer flowing-line
 *   renderer retained for the first four presets.
 * - [CoverGradientStyle.OCEAN_SWELL] draws broad, snake-like deep-blue
 *   wave bands that glide smoothly across one another.
 *
 * Geometry is deterministic for a given seed and continuous in time. Where
 * palettes move, [paletteColor] interpolates smoothly rather than snapping
 * between discrete stops. The math is pure and the original ribbon functions
 * remain covered by the JVM tests in CoverGradientTest.
 *
 * Both renderers use only Compose brushes and paths — no bitmaps, videos,
 * external animation libraries, or retained shader objects. The frame clock
 * runs only while the banner or selection sheet is composed.
 *
 * @param t unbounded time in seconds (0 = static first frame).
 * @param speed relative animation pace from the preset; multiplies [t].
 * @param seed deterministic per-preset shape and phase offset.
 * @param style visual family selected by the preset.
 */
internal fun DrawScope.drawCoverGradient(
    colors: List<Color>,
    t: Float,
    speed: Float,
    seed: Int,
    style: CoverGradientStyle = CoverGradientStyle.RIBBONS,
) {
    if (colors.size < 2 || size.width <= 0f || size.height <= 0f) return
    val w = size.width
    val h = size.height
    val tt = t * speed

    // The original four presets keep their ribbon renderer. The one new
    // deep-blue preset opts into the filled, snake-like ocean-current waves.
    if (style == CoverGradientStyle.OCEAN_SWELL) {
        drawOceanSwell(colors, tt, seed)
        return
    }

    // Background: a slow breath between the first two stops at the top,
    // falling off to a darkened base at the bottom (same depth cue the
    // photo covers have).
    val bgMix = 0.06f + 0.05f * sin(0.13f * tt + seed)
    val bgTop = lerp(colors[0], colors[1], bgMix.coerceIn(0f, 1f))
    val base = colors[0]
    val bgBottom = Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)
    drawRect(Brush.verticalGradient(listOf(bgTop, bgBottom)))

    val steps = 48
    val pad = 0.12f * w
    val x0 = -pad
    val span = w + 2f * pad

    for (layer in 0 until RIBBON_COUNT) {
        val p = ribbonParams(layer, seed)
        val shift = tt * p.colorRate
        val cL = paletteColor(colors, p.colorPos + shift)
        val cM = paletteColor(colors, p.colorPos + p.colorSpread * 0.5f + shift)
        val cR = paletteColor(colors, p.colorPos + p.colorSpread + shift)

        // Sample the centerline, then smooth it with a quadratic path
        // through the segment midpoints (control points at the samples).
        val pts = FloatArray((steps + 1) * 2)
        for (s in 0..steps) {
            val x = x0 + span * s / steps
            pts[s * 2] = x
            pts[s * 2 + 1] = ribbonCenterY(x, w, h, tt, p)
        }
        val path = ribbonPath(pts, steps)

        val ribbonWidth = h * p.thickness
        for ((mult, alphaFrac) in RIBBON_FALLOFF) {
            drawPath(
                path = path,
                color = Color(cM.red, cM.green, cM.blue, p.alpha * alphaFrac),
                style = Stroke(
                    width = (ribbonWidth * mult).coerceAtLeast(2f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        // Core: the bright inner line, colored by a horizontal gradient
        // that sweeps through the palette along the ribbon.
        val coreAlpha = (p.alpha * 0.75f).coerceAtMost(0.62f)
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                coreGradientColors(cL, cM, cR, coreAlpha),
            ),
            style = Stroke(
                width = (ribbonWidth * 0.55f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

/** How many ribbon layers each gradient draws. */
internal const val RIBBON_COUNT = 5

/**
 * Halo → shoulder falloff: (width multiplier, alpha fraction of the
 * ribbon's own opacity). File-static so the animation does not rebuild
 * pairs/arrays on every frame.
 */
private val RIBBON_FALLOFF = arrayOf(
    3.0f to 0.08f,
    2.1f to 0.13f,
    1.5f to 0.20f,
    1.0f to 0.32f,
)

/**
 * Samples the same smoothstep sweep used for palette motion into enough
 * evenly spaced brush stops that the core transitions gradually across x,
 * rather than linearly breaking at only three colors.
 */
private fun coreGradientColors(
    left: Color,
    middle: Color,
    right: Color,
    alpha: Float,
): List<Color> = List(9) { index ->
    val f = index / 8f
    val color = if (f < 0.5f) {
        lerp(left, middle, smoothstep(f * 2f))
    } else {
        lerp(middle, right, smoothstep((f - 0.5f) * 2f))
    }
    Color(color.red, color.green, color.blue, alpha)
}

/** Cubic Hermite easing with zero velocity at both ends. */
private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

/**
 * Deterministic per-(layer, seed, salt) hash in [0, 1).
 *
 * Integer-only xor/shift/multiply mixing (int32 wrapping, arithmetic
 * shifts) so the value is identical across runs, devices and the JVM
 * unit tests — no `Random`, no `hashCode` volatility.
 */
internal fun layerRand(layer: Int, seed: Int, salt: Int): Float {
    var x = seed * 0x9E3779B1.toInt() +
        layer * 0x85EBCA6B.toInt() +
        salt * 0xC2B2AE35.toInt()
    x = x xor (x shr 15)
    x *= 0x2545F491
    x = x xor (x shr 13)
    x *= 0x27D4EB2F
    x = x xor (x shr 16)
    // Low 24 bits → [0, 1). `and` keeps it non-negative.
    return (x and 0xFFFFFF) / 16777216f
}

/** Shape/opacity/color parameters for one ribbon layer. */
internal data class RibbonParams(
    /** Vertical anchor in 0..1 (relative to banner height). */
    val baseY: Float,
    /** Linear slope across the width. */
    val tilt: Float,
    /** Primary harmonic amplitude (fraction of height) / frequency / drift. */
    val amp1: Float,
    val freq1: Float,
    val drift1: Float,
    val phase1: Float,
    /** Detail harmonic — always a fraction of the primary. */
    val amp2: Float,
    val freq2: Float,
    val drift2: Float,
    val phase2: Float,
    /** Stroke thickness as a fraction of height. */
    val thickness: Float,
    /** Master opacity in 0..1. */
    val alpha: Float,
    /** Palette ring start, spread (in palette stops) and time drift. */
    val colorPos: Float,
    val colorSpread: Float,
    val colorRate: Float,
)

/**
 * Derives every parameter of ribbon [layer] for preset [seed].
 * Pure: identical inputs always yield identical outputs.
 */
internal fun ribbonParams(layer: Int, seed: Int): RibbonParams {
    fun r(salt: Int) = layerRand(layer, seed, salt)
    val amp1 = 0.045f + 0.075f * r(3)
    val freq1 = 1.1f + 1.3f * r(1)
    val drift1 = (0.10f + 0.14f * r(4)) * if (r(9) < 0.25f) -1f else 1f
    return RibbonParams(
        baseY = 0.16f + 0.58f * r(0),
        tilt = (r(2) - 0.5f) * 0.22f,
        amp1 = amp1,
        freq1 = freq1,
        drift1 = drift1,
        phase1 = r(5) * (2.0 * PI).toFloat(),
        amp2 = amp1 * (0.25f + 0.35f * r(6)),
        freq2 = freq1 * (1.5f + 1.0f * r(7)),
        drift2 = drift1 * (1.4f + 0.9f * r(8)),
        phase2 = r(10) * (2.0 * PI).toFloat(),
        thickness = 0.06f + 0.10f * r(11),
        alpha = 0.38f + 0.30f * r(12),
        colorPos = r(13),
        colorSpread = 1.0f + 1.5f * r(14),
        colorRate = (0.030f + 0.035f * r(15)) * if (r(16) < 0.35f) -1f else 1f,
    )
}

/**
 * Y coordinate of a ribbon's centerline at horizontal position [x].
 * Two traveling sines (primary + detail) over a tilted baseline — every
 * term is continuous in [t], so the ribbon undulates without snapping.
 */
internal fun ribbonCenterY(
    x: Float,
    w: Float,
    h: Float,
    t: Float,
    p: RibbonParams,
): Float {
    val u = x / w
    val twoPi = (2.0 * PI).toFloat()
    val phase1 = twoPi * p.freq1 * u + p.drift1 * t + p.phase1
    val phase2 = twoPi * p.freq2 * u + p.drift2 * t + p.phase2
    return h * (p.baseY + p.tilt * (u - 0.5f) +
        p.amp1 * sin(phase1) + p.amp2 * sin(phase2))
}

/**
 * Continuous color lookup around the palette ring.
 *
 * [pos] wraps modulo the number of stops (negatives included) and the
 * segment blends with smoothstep, so the result is continuous everywhere —
 * including across the wrap point — which is what replaces the old
 * discrete palette-index switching.
 */
internal fun paletteColor(colors: List<Color>, pos: Float): Color {
    val n = colors.size
    var p = pos % n
    if (p < 0f) p += n
    val i = p.toInt()
    val u = p - i
    val a = colors[i]
    val b = colors[(i + 1) % n]
    return lerp(a, b, smoothstep(u))
}

/** Smooths sampled centerline points into a C1 curve through their midpoints. */
private fun ribbonPath(pts: FloatArray, steps: Int): Path {
    val path = Path()
    // Start at the middle of the first segment so every segment (except
    // the last) is spanned by exactly one quadratic.
    path.moveTo((pts[0] + pts[2]) / 2f, (pts[1] + pts[3]) / 2f)
    for (s in 1 until steps) {
        val cx = pts[s * 2]
        val cy = pts[s * 2 + 1]
        val nx = pts[(s + 1) * 2]
        val ny = pts[(s + 1) * 2 + 1]
        path.quadraticBezierTo(cx, cy, (cx + nx) / 2f, (cy + ny) / 2f)
    }
    path.lineTo(pts[steps * 2], pts[steps * 2 + 1])
    return path
}

/**
 * Ocean current: three broad, semi-transparent water sheets roll across one
 * another. Unlike a stroked ribbon, each layer is a filled moving wave band.
 */
private fun DrawScope.drawOceanSwell(
    colors: List<Color>,
    t: Float,
    seed: Int,
) {
    val w = size.width
    val h = size.height
    val base = colors.first()
    drawRect(
        Brush.verticalGradient(
            listOf(lerp(base, colors[1], 0.19f), base, Color(base.red * 0.42f, base.green * 0.54f, base.blue * 0.62f)),
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(colors.last().copy(alpha = 0.10f), Color.Transparent),
            center = Offset(w * 0.72f, h * 0.32f),
            radius = w * 0.82f,
        ),
    )

    val twoPi = (2.0 * PI).toFloat()
    val steps = 54
    for (layer in 0 until 4) {
        fun r(salt: Int) = layerRand(layer, seed + 29, salt)
        val baseY = h * (0.22f + layer * 0.185f)
        val amplitude = h * (0.040f + 0.035f * r(0))
        val frequency = 0.78f + 0.48f * r(1)
        val phase = r(2) * twoPi
        val drift = 0.10f + 0.055f * r(3)
        val thickness = h * (0.105f + 0.035f * r(4))

        fun waveY(x: Float): Float {
            val u = x / w
            return baseY +
                amplitude * sin(twoPi * frequency * u + phase + t * drift) +
                amplitude * 0.34f * sin(twoPi * frequency * 1.78f * u - phase * 0.63f - t * drift * 1.21f)
        }

        val wave = Path()
        val pad = w * 0.08f
        for (sample in 0..steps) {
            val x = -pad + (w + 2f * pad) * sample / steps
            val y = waveY(x)
            if (sample == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        for (sample in steps downTo 0) {
            val x = -pad + (w + 2f * pad) * sample / steps
            val y = waveY(x) + thickness * (
                0.92f + 0.08f * sin(twoPi * 0.62f * (x / w) + phase + t * drift * 0.7f)
            )
            wave.lineTo(x, y)
        }
        wave.close()

        val near = paletteColor(colors, 1.05f + layer * 0.47f)
        val far = paletteColor(colors, 1.65f + layer * 0.43f)
        drawPath(
            path = wave,
            brush = Brush.verticalGradient(
                colors = listOf(
                    near.copy(alpha = 0.15f),
                    far.copy(alpha = 0.20f),
                    base.copy(alpha = 0.015f),
                ),
                startY = baseY - amplitude,
                endY = baseY + thickness + amplitude,
            ),
            blendMode = BlendMode.Plus,
        )

        val crest = Path()
        for (sample in 0..steps) {
            val x = -pad + (w + 2f * pad) * sample / steps
            val y = waveY(x)
            if (sample == 0) crest.moveTo(x, y) else crest.lineTo(x, y)
        }
        drawPath(
            path = crest,
            color = colors.last().copy(alpha = 0.13f + layer * 0.012f),
            style = Stroke(width = (h * 0.006f).coerceAtLeast(1.2f), cap = StrokeCap.Round),
            blendMode = BlendMode.Plus,
        )
    }
}

/**
 * The banner's animated-gradient layer.
 *
 * Lifecycle story (the "no work while Profile isn't visible" requirement):
 * the frame clock lives in [LaunchedEffect], so it exists only while this
 * composable is composed — navigating away from Profile disposes the banner
 * and cancels the loop, and backgrounding the app stops the Choreographer,
 * which suspends [androidx.compose.runtime.withFrameNanos] with zero CPU.
 * Every instance reads one process-wide monotonic timeline, so changing
 * gradient presets during a crossfade never restarts motion at t = 0. System
 * "Remove animations" ([rememberSystemReduceMotion]) renders a single static
 * frame at t = 0 and never starts the clock at all.
 *
 * One state write per frame invalidates only this layer's draw — the rest
 * of the profile (avatar, particles, content) does not recompose.
 */
@Composable
internal fun AnimatedCoverGradient(
    preset: CoverPreset,
    modifier: Modifier = Modifier,
) {
    // Palette conversion once per preset, not once per frame.
    val colors = remember(preset) { preset.gradientColors.map { Color(0xFF000000L or it) } }
    val reduceMotion = rememberSystemReduceMotion()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            withFrameNanos { now ->
                t = coverGradientTimelineSeconds(now, CoverGradientProcessOriginNanos)
            }
        }
    }
    Canvas(modifier = modifier) {
        drawCoverGradient(
            colors = colors,
            t = if (reduceMotion) 0f else t,
            speed = preset.gradientSpeed,
            seed = preset.gradientSeed,
            style = preset.gradientStyle,
        )
    }
}
