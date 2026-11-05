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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import quiz.thaton3app.nazo.data.profile.CoverPreset

/**
 * Draws one built-in animated-gradient cover into the current draw scope.
 *
 * Design goals (kept deliberately cheap — this runs behind the avatar on
 * modest phones): two brush draws per frame, no bitmaps, no shaders created
 * outside the frame, no allocation beyond the brushes themselves.
 *
 *  1. A full-bleed linear gradient whose axis slowly orbits — the motion is
 *     continuous forever because the phase (`t * speed`) is unbounded and
 *     every term is sin/cos, so there is no loop seam to flicker at.
 *  2. A soft radial glow that drifts across the field and breathes in
 *     alpha, picking a palette color that shifts as it travels.
 *
 * @param t unbounded time in seconds (0 = static first frame).
 * @param speed relative pace from the preset; multiplies [t].
 * @param seed per-preset phase offset so side-by-side previews differ.
 */
internal fun DrawScope.drawCoverGradient(
    colors: List<Color>,
    t: Float,
    speed: Float,
    seed: Int,
) {
    if (colors.size < 2 || size.width <= 0f || size.height <= 0f) return
    val w = size.width
    val h = size.height
    val phase = t * speed + seed * 1.618f

    // Layer 1 — orbiting linear field.
    val a = phase * 0.55f
    val startX = w * (0.5f + 0.5f * cos(a))
    val startY = h * (0.5f + 0.5f * sin(a * 0.8f))
    drawRect(
        Brush.linearGradient(
            colors = colors,
            start = Offset(startX, startY),
            end = Offset(w - startX, h - startY),
        )
    )

    // Layer 2 — drifting, breathing glow. Alpha stays in [0.14, 0.38] so
    // the layer never darkens the field or blows out to a white blob.
    val g = phase * 0.9f + PI.toFloat()
    val glowX = w * (0.5f + 0.42f * cos(g))
    val glowY = h * (0.45f + 0.35f * sin(g * 1.15f))
    val glowColor = colors[((abs(sin(g * 0.5f)) * (colors.size - 1)).toInt())
        .coerceIn(0, colors.lastIndex)]
    val glowAlpha = 0.26f + 0.12f * sin(g * 0.7f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent),
            center = Offset(glowX, glowY),
            radius = maxOf(w, h) * 0.65f,
        )
    )
}

/**
 * The banner's animated-gradient layer.
 *
 * Lifecycle story (the "no work while Profile isn't visible" requirement):
 * the frame clock lives in [LaunchedEffect], so it exists only while this
 * composable is composed — navigating away from Profile disposes the banner
 * and cancels the loop, and backgrounding the app stops the Choreographer,
 * which suspends [androidx.compose.runtime.withFrameNanos] with zero CPU.
 * System "Remove animations" ([rememberSystemReduceMotion]) renders a single
 * static frame at t = 0 and never starts the clock at all.
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
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                t = (now - startNanos) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier = modifier) {
        drawCoverGradient(
            colors = colors,
            t = if (reduceMotion) 0f else t,
            speed = preset.gradientSpeed,
            seed = preset.gradientSeed,
        )
    }
}
