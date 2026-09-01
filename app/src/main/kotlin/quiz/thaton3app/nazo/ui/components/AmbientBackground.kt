package quiz.thaton3app.nazo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val pulseFreq: Float,
    val pulsePhase: Float,
)

private data class RainDrop(
    val x: Float,
    val speed: Float,
    val length: Float,
    val alpha: Float,
    var currentY: Float,
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

private data class TouchRipple(
    val id: Long,
    val x: Float,
    val y: Float,
    val animatable: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val sparkles: List<TouchSparkle>,
)

private data class TouchSparkle(
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
    touchRipplesEnabled: Boolean = true,
) {
    val shapeParticles = remember { buildShapeParticles() }
    val constellationStars = remember { buildConstellationStars() }
    val rainDrops = remember { buildRainDrops() }
    val glowingOrbs = remember { buildGlowingOrbs() }
    val cache = remember { ParticleDrawCache() }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
            progress.animateTo(0f, animationSpec = tween(durationMillis = 45000, easing = LinearEasing))
        }
    }

    // Touch ripple state list
    val ripples = remember { mutableStateListOf<TouchRipple>() }
    val coroutineScope = rememberCoroutineScope()
    var nextRippleId = remember { 0L }

    val tapModifier = if (touchRipplesEnabled) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        event.changes.firstOrNull()?.position?.let { pos ->
                            val id = nextRippleId++
                            val anim = Animatable(0f)
                            val sparkCount = 8
                            val rng = Random(id)
                            val sparks = List(sparkCount) {
                                TouchSparkle(
                                    angle = rng.nextFloat() * (2f * PI.toFloat()),
                                    speed = 80f + rng.nextFloat() * 140f,
                                    size = 3f + rng.nextFloat() * 4f,
                                )
                            }
                            val ripple = TouchRipple(id = id, x = pos.x, y = pos.y, animatable = anim, sparkles = sparks)
                            ripples.add(ripple)
                            coroutineScope.launch {
                                anim.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = LinearEasing))
                                ripples.remove(ripple)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(tapModifier)) {
        val w = size.width
        val h = size.height
        val minDimension = if (w < h) w else h
        val t = progress.value
        val canvas = drawContext.canvas

        when (style) {
            "constellation" -> {
                // --- CONSTELLATION WEB STYLE ---
                val starRadius = minDimension * 0.012f
                val maxDist = minDimension * 0.28f

                // Draw connecting lines first
                for (i in constellationStars.indices) {
                    for (j in i + 1 until constellationStars.indices) {
                        val s1 = constellationStars[i]
                        val s2 = constellationStars[j]
                        val x1 = (s1.x + t * s1.vx * w).let { ((it % w) + w) % w }
                        val y1 = (s1.y + t * s1.vy * h).let { ((it % h) + h) % h }
                        val x2 = (s2.x + t * s2.vx * w).let { ((it % w) + w) % w }
                        val y2 = (s2.y + t * s2.vy * h).let { ((it % h) + h) % h }
                        val dist = hypot(x2 - x1, y2 - y1)
                        if (dist < maxDist) {
                            val alpha = (1f - (dist / maxDist)) * 0.18f
                            drawLine(
                                color = NazoPrimary.copy(alpha = alpha),
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 1.5f,
                            )
                        }
                    }
                }

                // Draw stars
                for (s in constellationStars) {
                    val x = (s.x + t * s.vx * w).let { ((it % w) + w) % w }
                    val y = (s.y + t * s.vy * h).let { ((it % h) + h) % h }
                    val pulse = (sin(t * s.pulseFreq * 20f + s.pulsePhase) * 0.3f + 0.7f).coerceIn(0.1f, 1f)
                    drawCircle(
                        color = NazoPrimary.copy(alpha = 0.18f * pulse),
                        radius = s.radius * 2.2f,
                        center = Offset(x, y),
                    )
                    drawCircle(
                        color = NazoPrimary.copy(alpha = 0.45f * pulse),
                        radius = s.radius,
                        center = Offset(x, y),
                    )
                }
            }

            "rain" -> {
                // --- DIGITAL RAIN STYLE ---
                val colWidth = w / 16f
                for (i in rainDrops.indices) {
                    val drop = rainDrops[i]
                    val x = (i * colWidth) + (colWidth * 0.5f)
                    val y = (t * drop.speed * h * 2f + drop.currentY) % (h + drop.length) - drop.length
                    val alpha = drop.alpha
                    drawRect(
                        color = NazoPrimary.copy(alpha = alpha * 0.25f),
                        topLeft = Offset(x - 1f, y),
                        size = androidx.compose.ui.geometry.Size(2f, drop.length),
                    )
                    drawCircle(
                        color = NazoSuccess.copy(alpha = alpha * 0.6f),
                        radius = 2.5f,
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
                        color = orb.color.copy(alpha = 0.08f),
                        radius = orb.radius * 1.8f,
                        center = Offset(cx, cy),
                    )
                    drawCircle(
                        color = orb.color.copy(alpha = 0.16f),
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
            val ringRadius = progressVal * minDimension * 0.35f
            val ringAlpha = (1f - progressVal) * 0.35f

            // Expanding ring
            drawCircle(
                color = NazoPrimary.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = Offset(ripple.x, ripple.y),
                style = Stroke(width = 3f * (1f - progressVal * 0.5f)),
            )

            // Burst sparkles
            for (sparkle in ripple.sparkles) {
                val dist = sparkle.speed * progressVal
                val sx = ripple.x + cos(sparkle.angle) * dist
                val sy = ripple.y + sin(sparkle.angle) * dist
                val sparkAlpha = (1f - progressVal) * 0.5f
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
    return List(22) {
        ConstellationStar(
            x = random.nextFloat(),
            y = random.nextFloat(),
            vx = (random.nextFloat() - 0.5f) * 0.015f,
            vy = (random.nextFloat() - 0.5f) * 0.015f,
            radius = 2.5f + random.nextFloat() * 2.5f,
            pulseFreq = 0.5f + random.nextFloat() * 1.5f,
            pulsePhase = random.nextFloat() * (2f * PI.toFloat()),
        )
    }
}

private fun buildRainDrops(): List<RainDrop> {
    val random = Random(4044)
    return List(16) {
        RainDrop(
            x = 0f,
            speed = 0.15f + random.nextFloat() * 0.25f,
            length = 40f + random.nextFloat() * 60f,
            alpha = 0.15f + random.nextFloat() * 0.25f,
            currentY = random.nextFloat() * 1000f,
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
