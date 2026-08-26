package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
    val duration: Int,      // full drift cycle in ms
    val ampX: Float,        // horizontal drift amplitude (fraction of width)
    val ampY: Float,        // vertical drift amplitude (fraction of height)
    val phase: Float,       // starting angle
    val rotSpeed: Float,    // degrees of rotation across a full cycle
    val alpha: Float,
)

/**
 * Subtle ambient background of floating shapes (circles, squares, triangles) that drift and
 * slowly rotate forever. Drawn once at the app root (outside AnimatedContent) so the animation
 * is continuous across every screen. Colors come from the active Nazo palette, so the particles
 * adapt to the selected accent. Rendered with very low alpha so they stay behind the content.
 */
@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    val colors = listOf(NazoPrimary, NazoSuccess, NazoError)
    val particles = remember { buildParticles() }
    val transition = rememberInfiniteTransition(label = "particles")
    val ts = particles.map { p ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = p.duration, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "particle-${p.colorIndex}",
        )
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val base = minOf(w, h)
        particles.forEachIndexed { i, p ->
            val t = ts[i].value
            val ang = p.phase + t * PI.toFloat() * 2f
            val cx = (p.baseX + cos(ang) * p.ampX) * w
            val cy = (p.baseY + sin(ang) * p.ampY) * h
            val r = p.size * base
            val color = colors[p.colorIndex % colors.size].copy(alpha = p.alpha)
            when (p.shape) {
                ParticleShape.CIRCLE -> drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(cx, cy),
                )
                ParticleShape.SQUARE -> rotate(degrees = t * p.rotSpeed, pivot = Offset(cx, cy)) {
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2, r * 2),
                    )
                }
                ParticleShape.TRIANGLE -> rotate(degrees = t * p.rotSpeed, pivot = Offset(cx, cy)) {
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
            rotSpeed = (r.nextFloat() - 0.5f) * 40f, // -20..20 deg per cycle
            alpha = 0.10f + r.nextFloat() * 0.12f,   // 0.10..0.22 subtle but visible
        )
    }
}
