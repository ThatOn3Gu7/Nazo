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
import androidx.compose.ui.graphics.Color
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

private enum class ParticleShape { CIRCLE, SQUARE, TRIANGLE, PENTAGON, STAR, HEXAGON, DIAMOND }

private data class Particle(
    val baseX: Float,
    val baseY: Float,
    val size: Float,
    val shape: ParticleShape,
    val driftFreqX: Float,
    val driftFreqY: Float,
    val phaseX: Float,
    val phaseY: Float,
    val ampX: Float,
    val ampY: Float,
    val rotSpeed: Float,
    val baseAlpha: Float,
)

/**
 * Geometry cache for the ambient background (final polish pack).
 *
 * The previous implementation rebuilt every Path, PathEffect and Stroke on
 * EVERY frame of a 45s infinite animation — dozens of allocations per frame,
 * i.e. steady GC pressure for the whole app lifetime on 4GB phones. All shape
 * geometry only depends on the canvas' min dimension, so it is built once
 * here (at the origin, unrotated) and rebuilt only if that dimension changes
 * (first layout / rotation). Per frame we now only translate/rotate the
 * canvas — zero allocations, pixel-identical output.
 */
private class ParticleDrawCache {
    var builtForMinDimension = -1f
        private set

    /** Indexed by [ParticleShape.ordinal]; null entry for CIRCLE (drawCircle). */
    val paths = arrayOfNulls<Path>(ParticleShape.values().size)
    var stroke: Stroke = Stroke()
    var circleRadius = 0f

    fun ensure(minDimension: Float, particleSize: Float) {
        if (minDimension == builtForMinDimension) return
        builtForMinDimension = minDimension

        val r = particleSize * minDimension
        // Same stroke/corner rounding as before (all 7 particles share size 0.10f).
        stroke = Stroke(
            width = (r * 0.08f).coerceAtLeast(3f),
            pathEffect = PathEffect.cornerPathEffect(r * 0.35f),
        )
        circleRadius = r * 0.9f

        // Radius multipliers below equalize visual weight across shapes
        // (identical constants to the original per-frame code).
        paths[ParticleShape.SQUARE.ordinal] = Path().apply {
            val sr = r * 0.8f
            moveTo(-sr, -sr); lineTo(sr, -sr); lineTo(sr, sr); lineTo(-sr, sr); close()
        }
        paths[ParticleShape.TRIANGLE.ordinal] = Path().apply {
            val tr = r * 1.15f
            val hDist = tr * 0.866f
            val vDist = tr * 0.5f
            moveTo(0f, -tr); lineTo(hDist, vDist); lineTo(-hDist, vDist); close()
        }
        paths[ParticleShape.PENTAGON.ordinal] = regularPolygon(r * 1.05f, 5)
        paths[ParticleShape.STAR.ordinal] = Path().apply {
            val str = r * 1.3f
            val points = 5
            for (i in 0 until (points * 2)) {
                val angle = (i * PI / points).toFloat() - (PI / 2).toFloat()
                val currentR = if (i % 2 == 0) str else str * 0.45f
                val px = currentR * cos(angle)
                val py = currentR * sin(angle)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        paths[ParticleShape.HEXAGON.ordinal] = regularPolygon(r * 1.0f, 6)
        paths[ParticleShape.DIAMOND.ordinal] = Path().apply {
            val dr = r * 1.15f
            moveTo(0f, -dr); lineTo(dr, 0f); lineTo(0f, dr); lineTo(-dr, 0f); close()
        }
    }

    private fun regularPolygon(radius: Float, sides: Int): Path = Path().apply {
        for (i in 0 until sides) {
            val angle = (i * 2 * PI / sides).toFloat() - (PI / 2).toFloat()
            val px = radius * cos(angle)
            val py = radius * sin(angle)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

/**
 * A highly polished, organic ambient background.
 * Optimized to exactly 7 large, uniquely colored elements to guarantee smooth 60fps
 * performance on all devices while keeping the UI clean and undistracted.
 */
@Composable
fun FloatingParticlesBackground(modifier: Modifier = Modifier) {
    val particles = remember { buildParticles() }
    val cache = remember { ParticleDrawCache() }

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
        cache.ensure(minDimension, particles[0].size)
        val t = progress.value
        val canvas = drawContext.canvas

        for (i in particles.indices) {
            val p = particles[i]
            val driftX = sin(t * p.driftFreqX * PI.toFloat() * 2f + p.phaseX) * p.ampX
            val driftY = cos(t * p.driftFreqY * PI.toFloat() * 2f + p.phaseY) * p.ampY

            val cx = (p.baseX + driftX).coerceIn(0.05f, 0.95f) * w
            val cy = (p.baseY + driftY).coerceIn(0.05f, 0.95f) * h

            // CIRCLE/SQUARE/TRIANGLE use brand tokens. The four accent hues below are
            // intentional decorative colors kept outside the theme palette so the ambient
            // background stays lively (no single Nazo* token maps to these hues).
            val color = when (p.shape) {
                ParticleShape.CIRCLE -> NazoPrimary
                ParticleShape.SQUARE -> NazoSuccess
                ParticleShape.TRIANGLE -> NazoError
                ParticleShape.PENTAGON -> Color(0xFF4FA4FF) // Soft Cyan / Electric Blue
                ParticleShape.STAR -> Color(0xFFFFA726)     // Warm Amber / Gold
                ParticleShape.HEXAGON -> Color(0xFF9C6ADE)  // Muted Purple / Lavender
                ParticleShape.DIAMOND -> Color(0xFF26A69A)  // Soft Teal
            }.copy(alpha = p.baseAlpha)

            if (p.shape == ParticleShape.CIRCLE) {
                // Rotation is a no-op on a circle; skip the canvas transform entirely.
                drawCircle(
                    color = color,
                    radius = cache.circleRadius,
                    center = Offset(cx, cy),
                    style = cache.stroke,
                )
            } else {
                val path = cache.paths[p.shape.ordinal] ?: continue
                // Manual save/translate/rotate instead of withTransform{} — the
                // trailing lambda would capture locals and allocate every frame.
                // Canvas.rotate(degrees) applies the exact rotation matrix the old
                // manual rx/ry math used: degrees = t * rotSpeed * 360.
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(t * p.rotSpeed * 360f)
                drawPath(path = path, color = color, style = cache.stroke)
                canvas.restore()
            }
        }
    }
}

private fun buildParticles(): List<Particle> {
    val random = Random(2027)
    val shapes = ParticleShape.values()

    // Exactly 7 items (one of each shape) ensures perfect smooth performance on potato phones
    // and keeps the background from looking cluttered.
    return List(7) { i ->
        // Distribute them in a 3x3 grid pattern so they start evenly spread across the screen
        val col = i % 3
        val row = i / 3

        val baseX = (col + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        val baseY = (row + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)

        Particle(
            baseX = baseX,
            baseY = baseY,
            size = 0.10f, // Enlarged size
            shape = shapes[i], // Exactly 1 of each of the 7 shapes
            driftFreqX = 1f + random.nextFloat() * 2f,
            driftFreqY = 1f + random.nextFloat() * 2f,
            phaseX = random.nextFloat() * (2f * PI.toFloat()),
            phaseY = random.nextFloat() * (2f * PI.toFloat()),
            ampX = 0.08f + random.nextFloat() * 0.07f,
            ampY = 0.08f + random.nextFloat() * 0.07f,
            rotSpeed = (random.nextFloat() * 2.0f + 0.5f) * (if (random.nextBoolean()) 1f else -1f),
            baseAlpha = 0.08f + random.nextFloat() * 0.10f // Kept translucent to remain a background element
        )
    }
}
