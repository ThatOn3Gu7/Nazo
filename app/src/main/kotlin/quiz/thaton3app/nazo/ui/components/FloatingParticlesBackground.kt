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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val size: Float,
    val shape: ParticleShape,
    val colorIndex: Int,
    val driftFreqX: Float,  // How many times it waves horizontally per cycle
    val driftFreqY: Float,  // How many times it waves vertically per cycle
    val phaseX: Float,
    val phaseY: Float,
    val ampX: Float,
    val ampY: Float,
    val rotSpeed: Float,    // How fast it tumbles
    val baseAlpha: Float,
)

/**
 * A highly polished, organic ambient background. 
 * Shapes have smoothed corners, slow tumbling rotation, and follow wide wandering paths 
 * so they don't clump together.
 */
@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    val colors = listOf(NazoPrimary, NazoSuccess, NazoError)
    val particles = remember { buildParticles() }

    // Ultra-slow, majestic continuous loop (45 seconds each way).
    // The ping-pong effect makes the shapes gracefully decelerate, stop, and reverse 
    // their tumbling and drifting, creating a natural "breathing" motion.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
            progress.animateTo(0f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDimension = if (w < h) w else h
        val t = progress.value

        particles.forEach { p ->
            // Smooth Lissajous curves for non-repeating, elegant wandering paths
            val driftX = sin(t * p.driftFreqX * PI.toFloat() * 2f + p.phaseX).toFloat() * p.ampX
            val driftY = cos(t * p.driftFreqY * PI.toFloat() * 2f + p.phaseY).toFloat() * p.ampY

            val cx = (p.baseX + driftX) * w
            val cy = (p.baseY + driftY) * h
            val r = p.size * minDimension

            // Calculate rotation for this specific frame
            val rot = t * p.rotSpeed * PI.toFloat() * 2f
            val cosA = cos(rot).toFloat()
            val sinA = sin(rot).toFloat()

            // Helper functions for manual 2D rotation (bypasses Compose rotate() constraint)
            fun rx(px: Float, py: Float) = cx + px * cosA - py * sinA
            fun ry(px: Float, py: Float) = cy + px * sinA + py * cosA

            // The secret sauce for "cool": rounded corners on all our sharp shapes!
            val cornerEffect = PathEffect.cornerPathEffect(r * 0.35f)
            val strokeWidth = (r * 0.08f).coerceAtLeast(3f)
            val strokeStyle = Stroke(width = strokeWidth, pathEffect = cornerEffect)

            val color = colors[p.colorIndex % colors.size].copy(alpha = p.baseAlpha)

            when (p.shape) {
                ParticleShape.CIRCLE -> {
                    // Circles don't need rotation, but they still benefit from the smooth strokes
                    drawCircle(
                        color = color,
                        radius = r,
                        center = Offset(cx, cy),
                        style = strokeStyle
                    )
                }
                ParticleShape.SQUARE -> {
                    // Manually rotated square coordinates
                    val path = Path().apply {
                        moveTo(rx(-r, -r), ry(-r, -r))
                        lineTo(rx(r, -r), ry(r, -r))
                        lineTo(rx(r, r), ry(r, r))
                        lineTo(rx(-r, r), ry(-r, r))
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
                ParticleShape.TRIANGLE -> {
                    // Manually rotated equilateral triangle
                    val hDist = r * 0.866f // r * sqrt(3)/2
                    val vDist = r * 0.5f
                    val path = Path().apply {
                        moveTo(rx(0f, -r), ry(0f, -r))       // Top vertex
                        lineTo(rx(hDist, vDist), ry(hDist, vDist))  // Bottom right
                        lineTo(rx(-hDist, vDist), ry(-hDist, vDist)) // Bottom left
                        close()
                    }
                    drawPath(path = path, color = color, style = strokeStyle)
                }
            }
        }
    }
}

private fun buildParticles(): List<Particle> {
    val random = Random(2025) 
    val shapes = ParticleShape.values()
    
    // 12 particles, distributed roughly on a grid so they don't clump at birth
    return List(12) { i ->
        val col = i % 3
        val row = i / 3
        
        // Base coordinate with a little jitter
        val baseX = (col + 0.5f) / 3f + (random.nextFloat() * 0.15f - 0.075f)
        val baseY = (row + 0.5f) / 4f + (random.nextFloat() * 0.15f - 0.075f)

        Particle(
            baseX = baseX,
            baseY = baseY,
            size = 0.05f + random.nextFloat() * 0.07f, // Big, beautiful shapes (5-12% of screen)
            shape = shapes[random.nextInt(shapes.size)],
            colorIndex = random.nextInt(3),
            driftFreqX = 1f + random.nextFloat() * 2f, 
            driftFreqY = 1f + random.nextFloat() * 2f,
            phaseX = random.nextFloat() * (2f * PI.toFloat()),
            phaseY = random.nextFloat() * (2f * PI.toFloat()),
            ampX = 0.1f + random.nextFloat() * 0.15f, // Wander up to 25% away from home
            ampY = 0.1f + random.nextFloat() * 0.15f,
            // Random direction and speed of tumbling
            rotSpeed = (random.nextFloat() * 3f + 1f) * (if (random.nextBoolean()) 1f else -1f),
            baseAlpha = 0.06f + random.nextFloat() * 0.10f // Super subtle, won't interrupt text
        )
    }
}
