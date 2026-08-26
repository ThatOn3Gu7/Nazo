package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess

private enum class ParticleShape { CIRCLE, SQUARE, TRIANGLE }

private data class Particle(
    val baseX: Float,
    val baseY: Float,
    val size: Float,        // fraction of the smaller screen dimension
    val shape: ParticleShape,
    val colorIndex: Int,
    val duration: Int,      // full drift cycle in ms (drives per-particle speed)
    val ampX: Float,        // horizontal drift amplitude (fraction of width)
    val ampY: Float,        // vertical drift amplitude (fraction of height)
    val phase: Float,       // starting angle
    val alpha: Float,
)

/**
 * Subtle ambient background of floating shapes (circles, squares, triangles) that drift forever.
 * Drawn once at the app root (outside AnimatedContent) so the animation is continuous across every
 * screen. Colors come from the active Nazo palette, so the particles adapt to the selected accent.
 * Rendered with low alpha so they stay behind the content.
 */
@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    val colors = listOf(NazoPrimary, NazoSuccess, NazoError)
    val particles = remember { buildParticles() }

    // One shared progress value (0 -> 1 -> 0) drives every particle; per-particle phase, amplitude
    // and a speed factor derived from each particle's duration give them independent-looking motion.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 12000, easing = LinearEasing))
            progress.animateTo(0f, animationSpec = tween(durationMillis = 12000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val base = if (w < h) w else h
        val t = progress.value
        particles.forEach { p ->
            val speed = 12000f / p.duration
            val ang = p.phase + t * PI.toFloat() * 2f * speed
            val cx = (p.baseX + cos(ang).toFloat() * p.ampX) * w
            val cy = (p.baseY + sin(ang).toFloat() * p.ampY) * h
            val r = p.size * base
            val color = colors[p.colorIndex % colors.size].copy(alpha = p.alpha)
            when (p.shape) {
                ParticleShape.CIRCLE -> drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(cx, cy),
                )
                ParticleShape.SQUARE -> drawRect(
                    color = color,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                )
                ParticleShape.TRIANGLE -> {
                    val path = Path().apply {
                        moveTo(cx, cy - r)
                        lineTo(cx - r, cy + r)
                        lineTo(cx + r, cy + r)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }
    }
}

private fun buildParticles(): List<Particle> {
    val r = Random(20240826)
    val shapes = ParticleShape.values()
    return List(16) {
        Particle(
            baseX = r.nextFloat(),
            baseY = r.nextFloat(),
            size = 0.012f + r.nextFloat() * 0.030f, // 1.2%..4.2% of min dimension
            shape = shapes[r.nextInt(shapes.size)],
            colorIndex = r.nextInt(3),
            duration = 7000 + r.nextInt(9000),      // 7s..16s gentle cycle
            ampX = 0.02f + r.nextFloat() * 0.04f,
            ampY = 0.03f + r.nextFloat() * 0.05f,
            phase = r.nextFloat() * PI.toFloat() * 2f,
            alpha = 0.10f + r.nextFloat() * 0.12f,   // 0.10..0.22 subtle but visible
        )
    }
}
