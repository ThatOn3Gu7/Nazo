package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import quiz.thaton3app.nazo.ui.theme.NazoError
import quiz.thaton3app.nazo.ui.theme.NazoPrimary
import quiz.thaton3app.nazo.ui.theme.NazoSuccess

private enum class ParticleShape { CIRCLE, SQUARE, TRIANGLE, PENTAGON, STAR, HEXAGON, DIAMOND }

private data class ShapeParticle(
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

private data class ConstellationStar(
    val fracX: Float,
    val fracY: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val pulseFreq: Float,
    val pulsePhase: Float,
)

private data class RainDrop(
    val fracX: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float,
)

private data class GlowingOrb(
    val baseX: Float,
    val baseY: Float,
    val radius: Float,
    val driftFreq: Float,
    val phase: Float,
    val amp: Float,
    val color: Color,
)

data class TouchRipple(
    val id: Long,
    val x: Float,
    val y: Float,
    val animatable: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val sparkles: List<TouchSparkle>,
)

data class TouchSparkle(
    val angle: Float,
    val speed: Float,
    val size: Float,
)

private class ParticleDrawCache {
    var builtForMinDimension = -1f
        private set

    val paths = arrayOfNulls<Path>(ParticleShape.values().size)
    var stroke: Stroke = Stroke()
    var circleRadius = 0f

    fun ensure(minDimension: Float, particleSize: Float) {
        if (minDimension == builtForMinDimension) return
        builtForMinDimension = minDimension

        val r = particleSize * minDimension
        stroke = Stroke(
            width = (r * 0.08f).coerceAtLeast(3f),
            pathEffect = PathEffect.cornerPathEffect(r * 0.35f),
        )
        circleRadius = r * 0.9f

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
 * Enhanced ambient background supporting multiple distinct visual variants
 * (Floating Shapes, Constellation Web, Digital Rain, Glowing Orbs) plus an
 * optional interactive touch ripple / sparkle burst system.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    style: String = "shapes", // "shapes" | "constellation" | "rain" | "orbs"
    ripples: List<TouchRipple> = emptyList(),
) {
    val shapeParticles = remember { buildShapeParticles() }
    val constellationStars = remember { buildConstellationStars() }
    val rainDrops = remember { buildRainDrops() }
    val glowingOrbs = remember { buildGlowingOrbs() }
    val cache = remember { ParticleDrawCache() }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 30000, easing = LinearEasing))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDimension = if (w < h) w else h
        val t = progress.value
        val canvas = drawContext.canvas

        when (style) {
            "constellation" -> {
                // --- CONSTELLATION WEB STYLE ---
                val maxDist = minDimension * 0.45f

                // Compute current screen positions for all stars across the entire screen
                val starPositions = constellationStars.map { s ->
                    val x = (s.fracX * w + t * s.vx * w * 1.5f).let { ((it % w) + w) % w }
                    val y = (s.fracY * h + t * s.vy * h * 1.5f).let { ((it % h) + h) % h }
                    Offset(x, y)
                }

                // Draw connecting lines first
                for (i in starPositions.indices) {
                    for (j in i + 1 until starPositions.size) {
                        val p1 = starPositions[i]
                        val p2 = starPositions[j]
                        val dist = hypot(p2.x - p1.x, p2.y - p1.y)
                        if (dist < maxDist) {
                            val alpha = (1f - (dist / maxDist)) * 0.4f
                            drawLine(
                                color = NazoPrimary.copy(alpha = alpha),
                                start = p1,
                                end = p2,
                                strokeWidth = 2f,
                            )
                        }
                    }
                }

                // Draw stars
                for (i in constellationStars.indices) {
                    val s = constellationStars[i]
                    val pos = starPositions[i]
                    val pulse = (sin(t * s.pulseFreq * 35f + s.pulsePhase) * 0.4f + 0.6f).coerceIn(0.2f, 1f)
                    drawCircle(
                        color = NazoPrimary.copy(alpha = 0.35f * pulse),
                        radius = s.radius * 2.5f,
                        center = pos,
                    )
                    drawCircle(
                        color = NazoPrimary.copy(alpha = 0.9f * pulse),
                        radius = s.radius * 1.2f,
                        center = pos,
                    )
                }
            }

            "rain" -> {
                // --- DIGITAL RAIN STYLE ---
                val colWidth = w / 18f
                for (i in rainDrops.indices) {
                    val drop = rainDrops[i]
                    val x = (drop.fracX * w)
                    // Falling downwards correctly: t increases 0..1, y moves downwards (addition)
                    val y = ((t * drop.speed * h * 1.8f + (i * 91f)) % (h + drop.length)) - drop.length
                    val alpha = drop.alpha

                    // Glowing vertical trail line
                    drawLine(
                        color = NazoPrimary.copy(alpha = alpha * 0.4f),
                        start = Offset(x, y),
                        end = Offset(x, y + drop.length),
                        strokeWidth = 2.5f,
                    )
                    // Bright lead droplet head at the bottom of the streak
                    drawCircle(
                        color = NazoSuccess.copy(alpha = alpha * 0.95f),
                        radius = 3.5f,
                        center = Offset(x, y + drop.length),
                    )
                }
            }

            "orbs" -> {
                // --- GLOWING ORBS STYLE ---
                for (orb in glowingOrbs) {
                    val driftX = sin(t * orb.driftFreq * PI.toFloat() * 2f + orb.phase) * orb.amp
                    val driftY = cos(t * orb.driftFreq * PI.toFloat() * 2f + orb.phase * 1.3f) * orb.amp
                    val cx = (orb.baseX + driftX).coerceIn(0.1f, 0.9f) * w
                    val cy = (orb.baseY + driftY).coerceIn(0.1f, 0.9f) * h
                    drawCircle(
                        color = orb.color.copy(alpha = 0.12f),
                        radius = orb.radius * 2f,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = orb.color.copy(alpha = 0.25f),
                        radius = orb.radius,
                        center = Offset(cx, cy),
                    )
                }
            }

            else -> {
                // --- CLASSIC FLOATING SHAPES (default "shapes") ---
                cache.ensure(minDimension, shapeParticles[0].size)

                for (i in shapeParticles.indices) {
                    val p = shapeParticles[i]
                    val driftX = sin(t * p.driftFreqX * PI.toFloat() * 2f + p.phaseX) * p.ampX
                    val driftY = cos(t * p.driftFreqY * PI.toFloat() * 2f + p.phaseY) * p.ampY

                    val cx = (p.baseX + driftX).coerceIn(0.05f, 0.95f) * w
                    val cy = (p.baseY + driftY).coerceIn(0.05f, 0.95f) * h

                    val color = when (p.shape) {
                        ParticleShape.CIRCLE -> NazoPrimary
                        ParticleShape.SQUARE -> NazoSuccess
                        ParticleShape.TRIANGLE -> NazoError
                        ParticleShape.PENTAGON -> Color(0xFF4FA4FF)
                        ParticleShape.STAR -> Color(0xFFFFA726)
                        ParticleShape.HEXAGON -> Color(0xFF9C6ADE)
                        ParticleShape.DIAMOND -> Color(0xFF26A69A)
                    }.copy(alpha = p.baseAlpha)

                    if (p.shape == ParticleShape.CIRCLE) {
                        drawCircle(
                            color = color,
                            radius = cache.circleRadius,
                            center = Offset(cx, cy),
                            style = cache.stroke,
                        )
                    } else {
                        val path = cache.paths[p.shape.ordinal] ?: continue
                        canvas.save()
                        canvas.translate(cx, cy)
                        canvas.rotate(t * p.rotSpeed * 360f)
                        drawPath(path = path, color = color, style = cache.stroke)
                        canvas.restore()
                    }
                }
            }
        }

        // --- INTERACTIVE TOUCH RIPPLES & SPARKLES ---
        for (ripple in ripples) {
            val progressVal = ripple.animatable.value
            val ringRadius = progressVal * minDimension * 0.45f
            val ringAlpha = (1f - progressVal) * 0.6f

            // Expanding ring
            drawCircle(
                color = NazoPrimary.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = Offset(ripple.x, ripple.y),
                style = Stroke(width = 4.5f * (1f - progressVal * 0.5f)),
            )

            // Burst sparkles
            for (sparkle in ripple.sparkles) {
                val dist = sparkle.speed * progressVal
                val sx = ripple.x + cos(sparkle.angle) * dist
                val sy = ripple.y + sin(sparkle.angle) * dist
                val sparkAlpha = (1f - progressVal) * 0.75f
                drawCircle(
                    color = NazoSuccess.copy(alpha = sparkAlpha),
                    radius = sparkle.size * (1f - progressVal * 0.3f),
                    center = Offset(sx, sy),
                )
            }
        }
    }
}

private fun buildShapeParticles(): List<ShapeParticle> {
    val random = Random(2027)
    val shapes = ParticleShape.values()
    return List(7) { i ->
        val col = i % 3
        val row = i / 3
        val baseX = (col + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        val baseY = (row + 0.5f) / 3f + (random.nextFloat() * 0.2f - 0.1f)
        ShapeParticle(
            baseX = baseX,
            baseY = baseY,
            size = 0.10f,
            shape = shapes[i],
            driftFreqX = 1f + random.nextFloat() * 2f,
            driftFreqY = 1f + random.nextFloat() * 2f,
            phaseX = random.nextFloat() * (2f * PI.toFloat()),
            phaseY = random.nextFloat() * (2f * PI.toFloat()),
            ampX = 0.08f + random.nextFloat() * 0.07f,
            ampY = 0.08f + random.nextFloat() * 0.07f,
            rotSpeed = (random.nextFloat() * 2.0f + 0.5f) * (if (random.nextBoolean()) 1f else -1f),
            baseAlpha = 0.08f + random.nextFloat() * 0.10f
        )
    }
}

private fun buildConstellationStars(): List<ConstellationStar> {
    val random = Random(3033)
    return List(32) {
        ConstellationStar(
            fracX = random.nextFloat(),
            fracY = random.nextFloat(),
            vx = (random.nextFloat() - 0.5f) * 0.4f,
            vy = (random.nextFloat() - 0.5f) * 0.4f,
            radius = 3f + random.nextFloat() * 3.5f,
            pulseFreq = 1.2f + random.nextFloat() * 2f,
            pulsePhase = random.nextFloat() * (2f * PI.toFloat()),
        )
    }
}

private fun buildRainDrops(): List<RainDrop> {
    val random = Random(4044)
    return List(22) {
        RainDrop(
            fracX = random.nextFloat(),
            speed = 0.7f + random.nextFloat() * 0.8f,
            length = 65f + random.nextFloat() * 85f,
            alpha = 0.35f + random.nextFloat() * 0.4f,
        )
    }
}

private fun buildGlowingOrbs(): List<GlowingOrb> {
    val random = Random(5055)
    val colors = listOf(NazoPrimary, NazoSuccess, Color(0xFF9C6ADE), Color(0xFFFFA726))
    return List(4) { i ->
        GlowingOrb(
            baseX = 0.2f + (i * 0.2f),
            baseY = 0.25f + ((i % 2) * 0.5f),
            radius = 70f + random.nextFloat() * 40f,
            driftFreq = 0.6f + random.nextFloat() * 0.6f,
            phase = random.nextFloat() * (2f * PI.toFloat()),
            amp = 0.12f + random.nextFloat() * 0.08f,
            color = colors[i % colors.size],
        )
    }
}
